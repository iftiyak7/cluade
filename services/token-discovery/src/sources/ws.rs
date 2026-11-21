//! WebSocket `logsSubscribe` source (production low-latency path without
//! Geyser).
//!
//! * Subscribes to logs mentioning the Pump.fun program at `processed`.
//! * Skips failed transactions (~80% of Pump.fun traffic).
//! * When logs are truncated, fetches the full transaction over HTTP so no
//!   event is lost (see `pf_solana::pump::tx`).
//! * Reconnects with exponential backoff, rotating endpoints; reports the
//!   slot range that may have been missed so the block source can backfill.
//!
//! NOT verified against a live socket from the development sandbox (its
//! proxy does not allow WebSocket upgrades). Message handling is unit-tested
//! against the documented `logsNotification` format.

use crate::pipeline::{now_us, Received};
use crate::redact::{host_label, scrub};
use crate::rpc::RpcPool;
use futures_util::{SinkExt, StreamExt};
use pf_solana::pump::tx::{events_from_logs, events_from_transaction, EventSource, TxEvents};
use pf_solana::pump::PROGRAM_ID;
use serde_json::{json, Value};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::mpsc;
use tokio_tungstenite::tungstenite::Message;

pub fn subscribe_request() -> String {
    json!({"jsonrpc": "2.0", "id": 1, "method": "logsSubscribe", "params": [{"mentions": [PROGRAM_ID]}, {"commitment": "processed"}]}).to_string()
}

#[derive(Debug, PartialEq)]
pub enum WsMessage {
    Subscribed(u64),
    Logs { slot: u64, tx: TxEvents },
    Other,
}

/// Parse one text frame.
pub fn parse_message(text: &str) -> WsMessage {
    let Ok(v) = serde_json::from_str::<Value>(text) else { return WsMessage::Other };
    if v.get("id").is_some() {
        if let Some(sub) = v.get("result").and_then(Value::as_u64) {
            return WsMessage::Subscribed(sub);
        }
    }
    if v.get("method").and_then(Value::as_str) != Some("logsNotification") {
        return WsMessage::Other;
    }
    let Some(res) = v.pointer("/params/result") else { return WsMessage::Other };
    let slot = res.pointer("/context/slot").and_then(Value::as_u64).unwrap_or(0);
    let val = &res["value"];
    let signature = val.get("signature").and_then(Value::as_str).unwrap_or("").to_string();
    let failed = !val.get("err").is_none_or(Value::is_null);
    let logs: Vec<&str> = val.get("logs").and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_str).collect()).unwrap_or_default();
    let parsed = if failed { None } else { Some(events_from_logs(&logs)) };
    let (events, truncated, decode_errors) = parsed.map(|p| (p.events, p.truncated, p.decode_errors)).unwrap_or_default();
    WsMessage::Logs { slot, tx: TxEvents { signature, slot, block_time: None, failed, source: EventSource::Logs, truncated_logs: truncated, events, decode_errors } }
}

pub async fn run(ws_urls: Vec<String>, rpc: Arc<RpcPool>, out: mpsc::Sender<Received>, gaps: mpsc::Sender<(u64, u64)>) -> anyhow::Result<()> {
    anyhow::ensure!(!ws_urls.is_empty(), "no WebSocket endpoints configured (RPC_PRIMARY_WS / RPC_SECONDARY_WS)");
    let secrets: Vec<String> = ws_urls.iter().chain(rpc.secrets()).cloned().collect();
    let mut attempt: u32 = 0;
    let mut last_slot: u64 = 0;
    loop {
        let url = &ws_urls[attempt as usize % ws_urls.len()];
        let label = host_label(url);
        tracing::info!(endpoint = %label, "connecting");
        match connect_and_stream(url, &rpc, &out, &mut last_slot).await {
            Ok(()) => tracing::warn!(endpoint = %label, "stream ended"),
            Err(e) => tracing::warn!(endpoint = %label, "stream error: {}", scrub(&e.to_string(), &secrets)),
        }
        if last_slot > 0 {
            // The block source can backfill from here once we are reconnected.
            let _ = gaps.try_send((last_slot, u64::MAX));
        }
        attempt += 1;
        let backoff = Duration::from_millis(250 * 2u64.pow(attempt.min(6)));
        tokio::time::sleep(backoff.min(Duration::from_secs(15))).await;
    }
}

async fn connect_and_stream(url: &str, rpc: &Arc<RpcPool>, out: &mpsc::Sender<Received>, last_slot: &mut u64) -> anyhow::Result<()> {
    let (mut ws, _) = tokio_tungstenite::connect_async(url).await?;
    ws.send(Message::Text(subscribe_request())).await?;
    let mut ping = tokio::time::interval(Duration::from_secs(15));
    let mut last_msg = Instant::now();
    loop {
        tokio::select! {
            _ = ping.tick() => {
                if last_msg.elapsed() > Duration::from_secs(30) {
                    anyhow::bail!("no messages for 30 s");
                }
                ws.send(Message::Ping(vec![])).await?;
            }
            msg = ws.next() => {
                let Some(msg) = msg else { return Ok(()) };
                let msg = msg?;
                last_msg = Instant::now();
                let text = match msg {
                    Message::Text(t) => t,
                    Message::Close(_) => return Ok(()),
                    _ => continue,
                };
                let received = Instant::now();
                match parse_message(&text) {
                    WsMessage::Subscribed(id) => tracing::info!(subscription = id, "subscribed"),
                    WsMessage::Logs { slot, tx } => {
                        *last_slot = (*last_slot).max(slot);
                        if tx.truncated_logs && !tx.failed {
                            // Fetch the full transaction; it may lag `processed` briefly.
                            let rpc = rpc.clone();
                            let out = out.clone();
                            let sig = tx.signature.clone();
                            tokio::spawn(async move {
                                for delay in [400u64, 800, 1600, 3200] {
                                    tokio::time::sleep(Duration::from_millis(delay)).await;
                                    if let Ok(v) = rpc.get_transaction(&sig).await {
                                        if let Ok(full) = events_from_transaction(&v) {
                                            let _ = out.send(Received { tx: full, at: received, at_wall_us: now_us() }).await;
                                            return;
                                        }
                                    }
                                }
                                tracing::warn!(signature = %sig, "backfill of truncated tx failed");
                            });
                        } else {
                            let _ = out.try_send(Received { tx, at: received, at_wall_us: now_us() });
                        }
                    }
                    WsMessage::Other => {}
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture_logs(name: &str) -> Vec<Value> {
        let p = format!("{}/../../packages/solana-adapters/tests/fixtures/{name}.json", env!("CARGO_MANIFEST_DIR"));
        let v: Value = serde_json::from_str(&std::fs::read_to_string(p).unwrap()).unwrap();
        v["meta"]["logMessages"].as_array().unwrap().clone()
    }

    fn notification(logs: Vec<Value>, err: Value) -> String {
        json!({"jsonrpc":"2.0","method":"logsNotification","params":{"result":{"context":{"slot":42},"value":{"signature":"SIG","err":err,"logs":logs}},"subscription":7}}).to_string()
    }

    #[test]
    fn parses_subscription_ack() {
        assert_eq!(parse_message(r#"{"jsonrpc":"2.0","result":7,"id":1}"#), WsMessage::Subscribed(7));
        assert_eq!(parse_message("not json"), WsMessage::Other);
    }

    #[test]
    fn parses_trade_notification() {
        let WsMessage::Logs { slot, tx } = parse_message(&notification(fixture_logs("buy_direct_v3"), Value::Null)) else { panic!() };
        assert_eq!(slot, 42);
        assert_eq!(tx.events.len(), 1);
        assert_eq!(tx.events[0].name(), "TradeEvent");
        assert!(!tx.truncated_logs);
    }

    #[test]
    fn failed_tx_is_marked_and_not_decoded() {
        let WsMessage::Logs { tx, .. } = parse_message(&notification(fixture_logs("buy_direct_v3"), json!({"InstructionError":[0,"Custom"]}))) else { panic!() };
        assert!(tx.failed && tx.events.is_empty());
    }

    #[test]
    fn truncated_notification_requests_backfill() {
        let WsMessage::Logs { tx, .. } = parse_message(&notification(fixture_logs("create_quote_mint"), Value::Null)) else { panic!() };
        assert!(tx.truncated_logs, "caller must fetch the full transaction");
    }

    #[test]
    fn subscribe_request_targets_pump() {
        assert!(subscribe_request().contains(PROGRAM_ID));
        assert!(subscribe_request().contains("processed"));
    }
}
