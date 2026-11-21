//! Block-following source over HTTP `getBlock`.
//!
//! Complete (no log truncation, emit_cpi data available) and works through
//! plain HTTPS, but adds ~1 slot of latency versus a stream. Used as a
//! fallback when WebSockets/Geyser are unavailable and to backfill gaps.

use crate::pipeline::{now_us, Received};
use crate::rpc::{RpcError, RpcPool};
use anyhow::Result;
use futures_util::{stream, StreamExt};
use pf_solana::pump::tx::{events_from_block_transaction, invokes_pump};
use serde_json::Value;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::{mpsc, watch};

const MAX_IN_FLIGHT: usize = 3;
const MAX_BEHIND: u64 = 150; // ~60 s; beyond this, skip ahead and count the gap

pub enum BlockOutcome {
    Block(Box<Value>),
    Skipped,
    NotYet,
}

async fn fetch(rpc: &RpcPool, slot: u64) -> Result<BlockOutcome> {
    match rpc.get_block(slot).await {
        Ok(v) if v.is_null() => Ok(BlockOutcome::NotYet),
        Ok(v) => Ok(BlockOutcome::Block(Box::new(v))),
        Err(e) => match e.downcast_ref::<RpcError>() {
            Some(r) if r.is_skipped_slot() => Ok(BlockOutcome::Skipped),
            Some(r) if r.is_not_yet_available() => Ok(BlockOutcome::NotYet),
            _ => Err(e),
        },
    }
}

/// Extract Pump.fun transactions from a block and send them downstream.
pub fn process_block(slot: u64, block: &Value, received: Instant, tx_out: &mpsc::Sender<Received>) -> (usize, usize) {
    let block_time = block.get("blockTime").and_then(Value::as_i64);
    let mut pump = 0;
    let mut sent = 0;
    for tx in block.get("transactions").and_then(Value::as_array).into_iter().flatten() {
        if !invokes_pump(tx) {
            continue;
        }
        pump += 1;
        if let Ok(parsed) = events_from_block_transaction(tx, slot, block_time) {
            if tx_out.try_send(Received { tx: parsed, at: received, at_wall_us: now_us() }).is_ok() {
                sent += 1;
            }
        }
    }
    (pump, sent)
}

pub async fn run(rpc: Arc<RpcPool>, start: Option<u64>, out: mpsc::Sender<Received>, tip_tx: watch::Sender<u64>, gaps: mpsc::Sender<(u64, u64)>) -> Result<()> {
    let mut next = match start {
        Some(s) => s,
        None => rpc.get_slot("confirmed").await?,
    };
    tracing::info!(slot = next, "block source starting");
    let mut tip: u64 = 0;
    loop {
        // Refresh the tip only once we have processed up to the last known
        // one: saves one request per round under tight rate limits.
        if next > tip {
            match rpc.get_slot("confirmed").await {
                Ok(t) => tip = t,
                Err(e) => {
                    tracing::warn!("getSlot failed: {e}");
                    tokio::time::sleep(Duration::from_millis(500)).await;
                    continue;
                }
            }
        }
        let _ = tip_tx.send(tip);
        if tip.saturating_sub(next) > MAX_BEHIND {
            let _ = gaps.try_send((next, tip - MAX_BEHIND));
            tracing::warn!(from = next, to = tip - MAX_BEHIND, "fell behind; skipping slots (gap recorded)");
            next = tip - MAX_BEHIND;
        }
        if next > tip {
            tokio::time::sleep(Duration::from_millis(150)).await;
            continue;
        }
        let slots: Vec<u64> = (next..=tip).collect();
        let mut results = stream::iter(slots.iter().copied()).map(|s| {
            let rpc = rpc.clone();
            async move { (s, fetch(&rpc, s).await) }
        }).buffered(MAX_IN_FLIGHT);
        while let Some((slot, res)) = results.next().await {
            match res {
                Ok(BlockOutcome::Block(b)) => {
                    process_block(slot, &b, Instant::now(), &out);
                    next = slot + 1;
                }
                Ok(BlockOutcome::Skipped) => next = slot + 1,
                Ok(BlockOutcome::NotYet) => break, // retry from here next round
                Err(e) => {
                    tracing::warn!(slot, "getBlock failed: {e}");
                    tokio::time::sleep(Duration::from_millis(300)).await;
                    break;
                }
            }
        }
    }
}
