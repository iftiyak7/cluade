//! Counters and latency percentiles (reported as P50/P95/P99).

use serde::Serialize;
use std::collections::BTreeMap;

const RESERVOIR: usize = 100_000;

#[derive(Debug, Default)]
pub struct Latency {
    samples: Vec<u64>,
    seen: u64,
}

impl Latency {
    pub fn record(&mut self, v: u64) {
        self.seen += 1;
        if self.samples.len() < RESERVOIR {
            self.samples.push(v);
        } else {
            // deterministic overwrite keeps recent behaviour visible
            let i = (self.seen as usize) % RESERVOIR;
            self.samples[i] = v;
        }
    }
    pub fn summary(&self) -> Option<Percentiles> {
        if self.samples.is_empty() {
            return None;
        }
        let mut s = self.samples.clone();
        s.sort_unstable();
        let p = |q: f64| s[(((s.len() - 1) as f64) * q).round() as usize];
        Some(Percentiles { n: self.seen, p50: p(0.50), p95: p(0.95), p99: p(0.99), max: *s.last().unwrap() })
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize)]
pub struct Percentiles {
    pub n: u64,
    pub p50: u64,
    pub p95: u64,
    pub p99: u64,
    pub max: u64,
}

#[derive(Debug, Default)]
pub struct Stats {
    pub counters: BTreeMap<&'static str, u64>,
    /// Receipt → envelope ready, microseconds (our own processing cost).
    pub decode_us: Latency,
    /// Block time → envelope ready, milliseconds (block time has 1 s resolution).
    pub block_lag_ms: Latency,
    /// Chain tip slot − event slot at processing time.
    pub slot_lag: Latency,
}

impl Stats {
    pub fn inc(&mut self, k: &'static str) {
        *self.counters.entry(k).or_default() += 1;
    }
    pub fn add(&mut self, k: &'static str, n: u64) {
        *self.counters.entry(k).or_default() += n;
    }
    pub fn snapshot(&self) -> serde_json::Value {
        serde_json::json!({
            "counters": self.counters,
            "decode_us": self.decode_us.summary(),
            "block_lag_ms": self.block_lag_ms.summary(),
            "slot_lag": self.slot_lag.summary(),
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn percentiles() {
        let mut l = Latency::default();
        for v in 1..=100 {
            l.record(v);
        }
        let s = l.summary().unwrap();
        assert_eq!((s.p50, s.p95, s.p99, s.max, s.n), (51, 95, 99, 100, 100));
        assert!(Latency::default().summary().is_none());
    }
}
