//! Regression test on REAL recorded mainnet data (2026-11-23, block source):
//! 40 tokens with all their trades and their mint accounts, replayed through
//! the full engine under the default paper config and a relaxed research
//! config. Asserts invariants, not profitability.

use flate2::read::GzDecoder;
use pf_config::TradingConfig;
use pf_events::{Envelope, Event};
use pf_trading_core::engine::{Effect, Engine};
use pf_trading_core::replay;
use std::collections::HashSet;
use std::io::Read;

const SOL_QUOTE: &str = "11111111111111111111111111111111";

fn fixture(name: &str) -> String {
    let p = format!("{}/tests/fixtures/{name}", env!("CARGO_MANIFEST_DIR"));
    let mut s = String::new();
    GzDecoder::new(std::fs::File::open(&p).unwrap_or_else(|e| panic!("{p}: {e}"))).read_to_string(&mut s).unwrap();
    s
}

fn config(path: &str) -> TradingConfig {
    let p = format!("{}/../../config/{path}", env!("CARGO_MANIFEST_DIR"));
    serde_json::from_str(&std::fs::read_to_string(p).unwrap()).unwrap()
}

struct Outcome {
    report: serde_json::Value,
    bought: Vec<String>,
}

fn run(cfg: TradingConfig, envs: &[Envelope], mints: &std::collections::HashMap<String, serde_json::Value>) -> Outcome {
    let mut e = Engine::new(cfg).unwrap();
    let mut bought = Vec::new();
    replay::run(&mut e, envs, mints, |eff| {
        if let Effect::Log(v) = eff {
            if v["kind"] == "buy_submitted" {
                bought.push(v["mint"].as_str().unwrap().to_string());
            }
        }
    });
    Outcome { report: serde_json::to_value(e.report()).unwrap(), bought }
}

#[test]
fn replay_of_real_mainnet_data_holds_invariants() {
    let envs: Vec<Envelope> = fixture("mainnet_replay_2026-11-23.jsonl.gz").lines().map(|l| serde_json::from_str(l).unwrap()).collect();
    let mints = replay::load_mint_cache(&fixture("mainnet_replay_mints_2026-11-23.jsonl.gz"));
    assert!(envs.len() > 1_000, "fixture should hold real trade flow");
    let tradeable: HashSet<String> = envs
        .iter()
        .filter_map(|e| match &e.event {
            Event::TokenDiscovered(d) if d.quote_mint == SOL_QUOTE && !d.mayhem_mode => Some(d.mint.clone()),
            _ => None,
        })
        .collect();

    for cfg_name in ["paper.json", "research-relaxed.json"] {
        let cfg = config(cfg_name);
        let a = run(cfg.clone(), &envs, &mints);
        let b = run(cfg.clone(), &envs, &mints);
        assert_eq!(a.report, b.report, "{cfg_name}: replay must be deterministic");
        assert_eq!(a.report["accounting_check_lamports"], 0, "{cfg_name}: wallet must reconcile");
        for m in &a.bought {
            assert!(tradeable.contains(m), "{cfg_name}: bought a non-SOL or mayhem token {m}");
        }
        let max_trade = cfg.risk.max_per_trade.0 as i64 + cfg.paper.network_fee.0 as i64;
        let closed = a.report["closed"]["trades"].as_u64().unwrap();
        if closed > 0 {
            let cost = a.report["closed"]["total_cost"].as_i64().unwrap();
            assert!(cost <= max_trade * closed as i64, "{cfg_name}: per-trade cap exceeded");
        }
        assert!(a.report["open_positions"].as_u64().unwrap() <= cfg.risk.max_open_positions as u64);
        eprintln!("{cfg_name}: bought {} closed {} net {} counters {}", a.bought.len(), closed, a.report["closed"]["net_pnl"], a.report["counters"]);
    }
}
