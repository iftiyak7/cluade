//! JSON-RPC over HTTP with an endpoint pool: health scoring, failover and
//! rate-limit backoff. Batch requests are deliberately not used — Helius
//! rejected them with 429 on the plan tested (2026-11-21).

use crate::redact::{host_label, scrub};
use anyhow::{anyhow, bail, Result};
use serde_json::{json, Value};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Mutex;
use std::time::{Duration, Instant};

#[derive(Debug)]
struct EndpointState {
    consecutive_failures: u32,
    ewma_latency_ms: f64,
    cooldown_until: Option<Instant>,
    requests: u64,
    failures: u64,
}

#[derive(Debug)]
pub struct Endpoint {
    url: String,
    pub label: String,
    state: Mutex<EndpointState>,
}

impl Endpoint {
    fn new(url: String) -> Self {
        Self {
            label: host_label(&url),
            url,
            state: Mutex::new(EndpointState { consecutive_failures: 0, ewma_latency_ms: 200.0, cooldown_until: None, requests: 0, failures: 0 }),
        }
    }

    /// Lower is better. Endpoints in cooldown sort last.
    fn score(&self, now: Instant) -> f64 {
        let s = self.state.lock().unwrap();
        let cooling = s.cooldown_until.is_some_and(|t| t > now);
        s.ewma_latency_ms * (1.0 + s.consecutive_failures as f64) + if cooling { 1e9 } else { 0.0 }
    }

    fn record(&self, ok: bool, latency: Duration, rate_limited: bool) {
        let mut s = self.state.lock().unwrap();
        s.requests += 1;
        if ok {
            s.consecutive_failures = 0;
            s.ewma_latency_ms = 0.8 * s.ewma_latency_ms + 0.2 * latency.as_secs_f64() * 1e3;
            s.cooldown_until = None;
        } else {
            s.failures += 1;
            s.consecutive_failures += 1;
            let backoff = if rate_limited { 500 } else { 250 } * 2u64.pow(s.consecutive_failures.min(6));
            s.cooldown_until = Some(Instant::now() + Duration::from_millis(backoff.min(30_000)));
        }
    }
}

#[derive(Debug, Clone, serde::Serialize)]
pub struct EndpointHealth {
    pub label: String,
    pub requests: u64,
    pub failures: u64,
    pub ewma_latency_ms: f64,
    pub healthy: bool,
}

pub struct RpcPool {
    http: reqwest::Client,
    endpoints: Vec<Endpoint>,
    secrets: Vec<String>,
    id: AtomicU64,
    pub failovers: AtomicU64,
    /// Client-side pacing shared by all endpoints (they usually share one
    /// plan/key, so failover does not add capacity). `RPC_MAX_RPS`.
    limiter: tokio::sync::Mutex<Instant>,
    min_interval: Duration,
    pub throttled: AtomicU64,
}

impl RpcPool {
    pub fn new(urls: Vec<String>) -> Result<Self> {
        if urls.is_empty() {
            bail!("no RPC endpoints configured (RPC_PRIMARY_HTTP / RPC_SECONDARY_HTTP)");
        }
        let http = reqwest::Client::builder().timeout(Duration::from_secs(20)).gzip(true).pool_max_idle_per_host(4).build()?;
        let rps: f64 = std::env::var("RPC_MAX_RPS").ok().and_then(|v| v.parse().ok()).filter(|v: &f64| *v > 0.0).unwrap_or(5.0);
        Ok(Self {
            http,
            secrets: urls.clone(),
            endpoints: urls.into_iter().map(Endpoint::new).collect(),
            id: AtomicU64::new(1),
            failovers: AtomicU64::new(0),
            limiter: tokio::sync::Mutex::new(Instant::now()),
            min_interval: Duration::from_secs_f64(1.0 / rps),
            throttled: AtomicU64::new(0),
        })
    }

    async fn pace(&self) {
        let mut next = self.limiter.lock().await;
        let now = Instant::now();
        if *next > now {
            self.throttled.fetch_add(1, Ordering::Relaxed);
            tokio::time::sleep(*next - now).await;
        }
        *next = (*next).max(now) + self.min_interval;
    }

    pub fn secrets(&self) -> &[String] {
        &self.secrets
    }

    fn ordered(&self) -> Vec<&Endpoint> {
        let now = Instant::now();
        let mut v: Vec<&Endpoint> = self.endpoints.iter().collect();
        v.sort_by(|a, b| a.score(now).total_cmp(&b.score(now)));
        v
    }

    pub fn health(&self) -> Vec<EndpointHealth> {
        let now = Instant::now();
        self.endpoints
            .iter()
            .map(|e| {
                let s = e.state.lock().unwrap();
                EndpointHealth { label: e.label.clone(), requests: s.requests, failures: s.failures, ewma_latency_ms: (s.ewma_latency_ms * 10.0).round() / 10.0, healthy: s.cooldown_until.is_none_or(|t| t <= now) }
            })
            .collect()
    }

    /// Call `method`, trying endpoints best-first. Returns `result`.
    /// RPC-level errors (`{"error": …}`) that are not transient are returned
    /// immediately without failover (e.g. "slot skipped").
    pub async fn call(&self, method: &str, params: Value) -> Result<Value> {
        let body = json!({"jsonrpc": "2.0", "id": self.id.fetch_add(1, Ordering::Relaxed), "method": method, "params": params});
        let mut last_err = None;
        for (i, ep) in self.ordered().into_iter().enumerate() {
            if i > 0 {
                self.failovers.fetch_add(1, Ordering::Relaxed);
            }
            self.pace().await;
            let t0 = Instant::now();
            let res = self.http.post(&ep.url).json(&body).send().await;
            let resp = match res {
                Ok(r) => r,
                Err(e) => {
                    ep.record(false, t0.elapsed(), false);
                    last_err = Some(anyhow!("{}: {}", ep.label, scrub(&e.to_string(), &self.secrets)));
                    continue;
                }
            };
            let status = resp.status();
            if status.as_u16() == 429 || status.is_server_error() {
                ep.record(false, t0.elapsed(), status.as_u16() == 429);
                last_err = Some(anyhow!("{}: HTTP {status}", ep.label));
                continue;
            }
            let v: Value = match resp.json().await {
                Ok(v) => v,
                Err(e) => {
                    ep.record(false, t0.elapsed(), false);
                    last_err = Some(anyhow!("{}: bad body: {}", ep.label, scrub(&e.to_string(), &self.secrets)));
                    continue;
                }
            };
            ep.record(true, t0.elapsed(), false);
            if let Some(err) = v.get("error") {
                return Err(RpcError { code: err.get("code").and_then(Value::as_i64).unwrap_or(0), message: err.get("message").and_then(Value::as_str).unwrap_or("").to_string() }.into());
            }
            return Ok(v.get("result").cloned().unwrap_or(Value::Null));
        }
        Err(last_err.unwrap_or_else(|| anyhow!("all endpoints failed")))
    }

    pub async fn get_transaction(&self, signature: &str) -> Result<Value> {
        self.call("getTransaction", json!([signature, {"encoding": "json", "maxSupportedTransactionVersion": 1, "commitment": "confirmed"}])).await
    }

    pub async fn get_slot(&self, commitment: &str) -> Result<u64> {
        self.call("getSlot", json!([{"commitment": commitment}])).await?.as_u64().ok_or_else(|| anyhow!("getSlot: not a number"))
    }

    pub async fn get_block(&self, slot: u64) -> Result<Value> {
        self.call("getBlock", json!([slot, {"encoding": "json", "maxSupportedTransactionVersion": 1, "transactionDetails": "full", "rewards": false, "commitment": "confirmed"}])).await
    }
}

#[derive(Debug, thiserror::Error)]
#[error("rpc error {code}: {message}")]
pub struct RpcError {
    pub code: i64,
    pub message: String,
}

impl RpcError {
    /// Slot was skipped by the leader or is not available (normal on Solana).
    pub fn is_skipped_slot(&self) -> bool {
        matches!(self.code, -32007 | -32009) || self.message.contains("skipped")
    }
    /// Block not yet available at this commitment — retry shortly.
    pub fn is_not_yet_available(&self) -> bool {
        self.code == -32004 || self.message.contains("not available")
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn failing_endpoint_sorts_last() {
        let pool = RpcPool::new(vec!["https://a.example/?api-key=x".into(), "https://b.example/".into()]).unwrap();
        pool.endpoints[0].record(false, Duration::from_millis(10), true);
        assert_eq!(pool.ordered()[0].label, "b.example");
        pool.endpoints[0].record(true, Duration::from_millis(10), false);
        pool.endpoints[1].record(false, Duration::from_millis(10), false);
        assert_eq!(pool.ordered()[0].label, "a.example");
        assert!(pool.health().iter().all(|h| !h.label.contains("api-key")));
    }
}
