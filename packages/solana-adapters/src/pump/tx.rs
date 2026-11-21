//! Extract Pump.fun events from transaction logs and inner instructions.
//!
//! Two sources, because each fails differently:
//!
//! * **Logs** (`Program data: …`) arrive with `logsSubscribe` and Geyser, but
//!   validators truncate long log output (`Log truncated`). On 2026-11-21 one
//!   in ~157 successful Pump.fun transactions was truncated and lost a
//!   TradeEvent from its logs.
//! * **emit_cpi inner instructions** (Pump.fun self-invokes with
//!   `EMIT_CPI_TAG || event`) are complete, but only present in a full
//!   transaction (`getTransaction`, Geyser transaction stream).
//!
//! When inner-instruction events are available they are authoritative;
//! otherwise logs are used and `truncated` tells the caller to backfill.

use super::events::{decode_event, PumpEvent, EMIT_CPI_TAG};
use super::PROGRAM_ID;
use base64::Engine;
use serde_json::Value;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EventSource {
    Logs,
    InnerInstructions,
}

#[derive(Debug, Clone, PartialEq)]
pub struct LogEvents {
    pub events: Vec<PumpEvent>,
    /// The validator cut the logs; events may be missing.
    pub truncated: bool,
    /// Decode failures (data attributed to Pump.fun that did not parse).
    pub decode_errors: Vec<String>,
}

/// Parse a transaction's log messages. `Program data:` lines are attributed
/// to the program on top of the invoke stack, so data emitted by other
/// programs (routers, aggregators) is ignored even if it collides.
pub fn events_from_logs(logs: &[impl AsRef<str>]) -> LogEvents {
    let mut stack: Vec<&str> = Vec::new();
    let mut out = LogEvents { events: Vec::new(), truncated: false, decode_errors: Vec::new() };
    for line in logs {
        let line = line.as_ref();
        if let Some(rest) = line.strip_prefix("Program ") {
            if let Some(data) = rest.strip_prefix("data: ") {
                if stack.last() == Some(&PROGRAM_ID) {
                    for chunk in data.split(' ') {
                        match base64::engine::general_purpose::STANDARD.decode(chunk) {
                            Ok(bytes) => match decode_event(&bytes) {
                                Ok(Some(ev)) => out.events.push(ev),
                                Ok(None) => {}
                                Err(e) => out.decode_errors.push(e.to_string()),
                            },
                            Err(e) => out.decode_errors.push(format!("base64: {e}")),
                        }
                    }
                }
                continue;
            }
            let mut parts = rest.split(' ');
            let (Some(id), Some(verb)) = (parts.next(), parts.next()) else { continue };
            match verb {
                "invoke" => stack.push(id),
                "success" | "failed:" => {
                    // pop to the matching frame (robust to missing lines)
                    if let Some(i) = stack.iter().rposition(|p| *p == id) {
                        stack.truncate(i);
                    }
                }
                _ => {}
            }
        } else if line.starts_with("Log truncated") {
            out.truncated = true;
        }
    }
    out
}

#[derive(Debug, Clone, PartialEq)]
pub struct TxEvents {
    pub signature: String,
    pub slot: u64,
    pub block_time: Option<i64>,
    pub failed: bool,
    pub source: EventSource,
    pub truncated_logs: bool,
    pub events: Vec<PumpEvent>,
    pub decode_errors: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum TxParseError {
    #[error("missing field {0}")]
    Missing(&'static str),
}

fn account_keys(tx: &Value) -> Result<Vec<String>, TxParseError> {
    let msg = tx.pointer("/transaction/message").ok_or(TxParseError::Missing("transaction.message"))?;
    let mut keys: Vec<String> = msg
        .get("accountKeys")
        .and_then(Value::as_array)
        .ok_or(TxParseError::Missing("accountKeys"))?
        .iter()
        // `json` encoding: strings; `jsonParsed`: objects with `pubkey`
        .filter_map(|k| k.as_str().map(str::to_owned).or_else(|| k.get("pubkey").and_then(Value::as_str).map(str::to_owned)))
        .collect();
    if let Some(la) = tx.pointer("/meta/loadedAddresses") {
        for part in ["writable", "readonly"] {
            if let Some(arr) = la.get(part).and_then(Value::as_array) {
                keys.extend(arr.iter().filter_map(|k| k.as_str().map(str::to_owned)));
            }
        }
    }
    Ok(keys)
}

/// Parse a `getTransaction` result (encoding `json`, any tx version).
pub fn events_from_transaction(tx: &Value) -> Result<TxEvents, TxParseError> {
    let slot = tx.get("slot").and_then(Value::as_u64).ok_or(TxParseError::Missing("slot"))?;
    events_from_block_transaction(tx, slot, tx.get("blockTime").and_then(Value::as_i64))
}

/// True if the transaction's logs show a Pump.fun invocation. Cheap
/// pre-filter for block scanning (failed txs that never invoked are skipped).
pub fn invokes_pump(tx: &Value) -> bool {
    tx.pointer("/meta/logMessages").and_then(Value::as_array).is_some_and(|logs| {
        logs.iter().filter_map(Value::as_str).any(|l| l.starts_with("Program ") && l.contains(PROGRAM_ID) && l.contains(" invoke ["))
    })
}

/// Parse one entry of `getBlock(...).transactions`, which carries no
/// per-transaction `slot`/`blockTime`.
pub fn events_from_block_transaction(tx: &Value, slot: u64, block_time: Option<i64>) -> Result<TxEvents, TxParseError> {
    let signature = tx.pointer("/transaction/signatures/0").and_then(Value::as_str).ok_or(TxParseError::Missing("signature"))?.to_owned();
    let meta = tx.get("meta").ok_or(TxParseError::Missing("meta"))?;
    let failed = !meta.get("err").is_none_or(Value::is_null);
    let logs: Vec<&str> = meta.get("logMessages").and_then(Value::as_array).map(|a| a.iter().filter_map(Value::as_str).collect()).unwrap_or_default();
    let from_logs = events_from_logs(&logs);

    let keys = account_keys(tx)?;
    let mut cpi = Vec::new();
    let mut errors = from_logs.decode_errors.clone();
    if let Some(groups) = meta.get("innerInstructions").and_then(Value::as_array) {
        for ix in groups.iter().filter_map(|g| g.get("instructions").and_then(Value::as_array)).flatten() {
            let pid = ix.get("programIdIndex").and_then(Value::as_u64).and_then(|i| keys.get(i as usize));
            if pid.map(String::as_str) != Some(PROGRAM_ID) {
                continue;
            }
            let Some(data) = ix.get("data").and_then(Value::as_str) else { continue };
            let Ok(bytes) = bs58::decode(data).into_vec() else { continue };
            if bytes.len() < 16 || bytes[..8] != EMIT_CPI_TAG {
                continue;
            }
            match decode_event(&bytes[8..]) {
                Ok(Some(ev)) => cpi.push(ev),
                Ok(None) => {}
                Err(e) => errors.push(e.to_string()),
            }
        }
    }
    let (source, events) = if cpi.is_empty() { (EventSource::Logs, from_logs.events) } else { (EventSource::InnerInstructions, cpi) };
    Ok(TxEvents {
        signature,
        slot,
        block_time,
        failed,
        source,
        truncated_logs: from_logs.truncated,
        events,
        decode_errors: errors,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ignores_data_from_other_programs() {
        // A router logging data with the TradeEvent discriminator must not be decoded.
        let b64 = base64::engine::general_purpose::STANDARD.encode(super::super::events::DISC_TRADE);
        let logs = [
            "Program RouterXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX invoke [1]".to_string(),
            format!("Program data: {b64}"),
            "Program RouterXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX success".to_string(),
        ];
        let r = events_from_logs(&logs);
        assert!(r.events.is_empty() && r.decode_errors.is_empty());
    }

    #[test]
    fn detects_truncation() {
        let r = events_from_logs(&["Program 6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P invoke [1]", "Log truncated"]);
        assert!(r.truncated);
    }
}
