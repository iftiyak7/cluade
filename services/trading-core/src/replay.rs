//! Deterministic replay of recorded envelopes through the engine.

use crate::engine::{Effect, Engine};
use base64::Engine as _;
use pf_events::{Envelope, Event};
use pf_solana::token::{decode_mint, MintInfo};
use serde_json::Value;
use std::collections::HashMap;

pub fn slot_of(env: &Envelope) -> u64 {
    match &env.event {
        Event::TokenDiscovered(d) => d.slot,
        Event::TradeObserved(t) => t.slot,
        Event::CurveCompleted { slot, .. } => *slot,
        _ => 0,
    }
}

/// Decode a `getAccountInfo` result (base64) into a mint, checking owner.
pub fn decode_mint_value(v: &Value) -> Result<MintInfo, String> {
    let val = v.get("value").ok_or("no value")?;
    if val.is_null() {
        return Err("mint account not found".into());
    }
    let owner = val.get("owner").and_then(Value::as_str).unwrap_or("");
    if owner != pf_solana::pump::TOKEN_2022_PROGRAM && owner != pf_solana::pump::TOKEN_PROGRAM {
        return Err(format!("unexpected mint owner {owner}"));
    }
    let data = val.pointer("/data/0").and_then(Value::as_str).ok_or("no data")?;
    let bytes = base64::engine::general_purpose::STANDARD.decode(data).map_err(|e| e.to_string())?;
    decode_mint(&bytes, owner == pf_solana::pump::TOKEN_2022_PROGRAM).map_err(|e| e.to_string())
}

/// Mint cache line format: `{"mint": "...", "account": <getAccountInfo result>}`.
pub fn load_mint_cache(text: &str) -> HashMap<String, Value> {
    text.lines()
        .filter_map(|l| serde_json::from_str::<Value>(l).ok())
        .filter_map(|v| Some((v.get("mint")?.as_str()?.to_string(), v["account"].clone())))
        .collect()
}

/// Feed envelopes in order; tick once per second of recorded wall time.
/// `on_effect` sees every non-mint effect (DB ops, logs).
pub fn run<'a>(engine: &mut Engine, envelopes: impl IntoIterator<Item = &'a Envelope>, mints: &HashMap<String, Value>, mut on_effect: impl FnMut(Effect)) -> u64 {
    let (mut tip, mut last_tick, mut n) = (0u64, 0i64, 0u64);
    let mut drain = |engine: &mut Engine| loop {
        let effects = engine.drain_effects();
        if effects.is_empty() {
            break;
        }
        for e in effects {
            match e {
                Effect::FetchMint(m) => {
                    let r = mints.get(&m).map(decode_mint_value).unwrap_or_else(|| Err("not in mint cache".into()));
                    engine.on_mint(&m, r);
                }
                other => on_effect(other),
            }
        }
    };
    for env in envelopes {
        let w = env.produced_at;
        tip = tip.max(slot_of(env));
        engine.handle(env, w, tip);
        drain(engine);
        if w - last_tick >= 1_000 {
            engine.tick(w, tip);
            drain(engine);
            last_tick = w;
        }
        n += 1;
    }
    engine.tick(last_tick + 1_000, tip);
    drain(engine);
    n
}
