//! Pump.fun discovery library: sources (WebSocket, blocks), decoding
//! pipeline, RPC pool. Used by the `pf-discovery` binary and in-process by
//! `trading-core`.

pub mod pipeline;
pub mod redact;
pub mod rpc;
pub mod sources;
pub mod stats;

/// Non-empty values of the given environment variables, in order.
pub fn env_list(keys: &[&str]) -> Vec<String> {
    keys.iter().filter_map(|k| std::env::var(k).ok()).filter(|v| !v.trim().is_empty()).collect()
}
