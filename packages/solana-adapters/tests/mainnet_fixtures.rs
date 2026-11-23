//! Verification against real mainnet data captured 2026-11-21.
//!
//! Fixtures are unmodified `getTransaction` results (public chain data).
//! `expected_events.json` was produced by an independent, IDL-driven Python
//! decoder, so these tests cross-check two implementations.

use base64::Engine;
use pf_solana::curve::{CurveState, FeeSchedule, INITIAL_REAL_TOKENS, INITIAL_VIRTUAL_SOL, INITIAL_VIRTUAL_TOKENS, TOKEN_TOTAL_SUPPLY};
use pf_solana::pump::accounts::decode_global;
use pf_solana::pump::events::{PumpEvent, TradeEvent};
use pf_solana::pump::tx::{events_from_logs, events_from_transaction, EventSource};
use pf_types::{Lamports, TokenAmount};
use serde_json::Value;

const FIXTURES: [&str; 9] = ["amm_migration", "buy_direct_v3", "buy_routed", "create_quote_mint", "create_sol", "curve_complete", "sell_routed", "sell_routed_2", "trade_quote_mint"];

fn load(name: &str) -> Value {
    let p = format!("{}/tests/fixtures/{name}.json", env!("CARGO_MANIFEST_DIR"));
    serde_json::from_str(&std::fs::read_to_string(p).unwrap()).unwrap()
}

fn expected() -> Value {
    load("expected_events")
}

#[test]
fn decoded_events_match_independent_decoder() {
    let exp = expected();
    for name in FIXTURES {
        let got = events_from_transaction(&load(name)).unwrap();
        let want = &exp[name]["cpi"];
        assert_eq!(got.source, EventSource::InnerInstructions, "{name}");
        let want_named: Vec<&Value> = want.as_array().unwrap().iter().filter(|e| e["_name"] != "CloseUserVolumeAccumulatorEvent").collect();
        let got_named: Vec<&PumpEvent> = got.events.iter().filter(|e| !matches!(e, PumpEvent::Other { .. })).collect();
        assert_eq!(got_named.len(), want_named.len(), "{name}: {:?}", got.events.iter().map(|e| e.name()).collect::<Vec<_>>());
        for (g, w) in got_named.iter().zip(want_named) {
            assert_eq!(g.name(), w["_name"].as_str().unwrap(), "{name}");
            match g {
                PumpEvent::Trade(t) => {
                    assert_eq!(t.mint.to_string(), w["mint"].as_str().unwrap());
                    assert_eq!(t.user.to_string(), w["user"].as_str().unwrap());
                    assert_eq!(t.creator.to_string(), w["creator"].as_str().unwrap());
                    for (field, v) in [
                        ("sol_amount", t.sol_amount),
                        ("token_amount", t.token_amount),
                        ("virtual_sol_reserves", t.virtual_sol_reserves),
                        ("virtual_token_reserves", t.virtual_token_reserves),
                        ("real_sol_reserves", t.real_sol_reserves),
                        ("real_token_reserves", t.real_token_reserves),
                        ("fee_basis_points", t.fee_basis_points),
                        ("fee", t.fee),
                        ("creator_fee_basis_points", t.creator_fee_basis_points),
                        ("creator_fee", t.creator_fee),
                    ] {
                        assert_eq!(v, w[field].as_u64().unwrap(), "{name}.{field}");
                    }
                    assert_eq!(t.is_buy, w["is_buy"].as_bool().unwrap());
                    assert_eq!(t.ix_name, w["ix_name"].as_str().unwrap());
                    let tail = t.tail.as_ref().expect("tail present on current program");
                    assert_eq!(tail.quote_mint.to_string(), w["quote_mint"].as_str().unwrap());
                    assert_eq!(tail.mayhem_mode, w["mayhem_mode"].as_bool().unwrap());
                    assert_eq!(tail.buyback_fee, w["buyback_fee"].as_u64().unwrap());
                    assert_eq!(t.unknown_trailing_bytes as u64, w["_extra_bytes"].as_u64().unwrap(), "{name}: IDL drift");
                }
                PumpEvent::Create(c) => {
                    assert_eq!(c.name, w["name"].as_str().unwrap());
                    assert_eq!(c.symbol, w["symbol"].as_str().unwrap());
                    assert_eq!(c.uri, w["uri"].as_str().unwrap());
                    assert_eq!(c.mint.to_string(), w["mint"].as_str().unwrap());
                    assert_eq!(c.bonding_curve.to_string(), w["bonding_curve"].as_str().unwrap());
                    assert_eq!(c.creator.to_string(), w["creator"].as_str().unwrap());
                    assert_eq!(c.virtual_sol_reserves, w["virtual_sol_reserves"].as_u64().unwrap());
                    let tail = c.tail.as_ref().unwrap();
                    assert_eq!(tail.token_program.to_string(), w["token_program"].as_str().unwrap());
                    assert_eq!(tail.quote_mint.to_string(), w["quote_mint"].as_str().unwrap());
                    assert_eq!(tail.creator_fee_bps, w["creator_fee_bps"].as_u64().unwrap());
                }
                PumpEvent::Complete(c) => {
                    assert_eq!(c.mint.to_string(), w["mint"].as_str().unwrap());
                    assert_eq!(c.bonding_curve.to_string(), w["bonding_curve"].as_str().unwrap());
                    assert_eq!(c.quote_mint.map(|q| q.to_string()).as_deref(), w["quote_mint"].as_str());
                }
                PumpEvent::CompleteAmmMigration(m) => {
                    assert_eq!(m.mint.to_string(), w["mint"].as_str().unwrap());
                    assert_eq!(m.pool.to_string(), w["pool"].as_str().unwrap());
                    assert_eq!(m.mint_amount, w["mint_amount"].as_u64().unwrap());
                    assert_eq!(m.sol_amount, w["sol_amount"].as_u64().unwrap());
                    assert_eq!(m.pool_migration_fee, w["pool_migration_fee"].as_u64().unwrap());
                }
                _ => {}
            }
        }
    }
}

#[test]
fn migration_only_visible_via_emit_cpi_when_logs_truncated() {
    let tx = load("amm_migration");
    let logs: Vec<String> = tx["meta"]["logMessages"].as_array().unwrap().iter().map(|v| v.as_str().unwrap().to_owned()).collect();
    let from_logs = events_from_logs(&logs);
    assert!(from_logs.truncated && from_logs.events.is_empty(), "logs-only discovery misses this migration");
    let full = events_from_transaction(&tx).unwrap();
    assert!(matches!(full.events.as_slice(), [PumpEvent::CompleteAmmMigration(_)]));
}

#[test]
fn truncated_logs_lose_events_but_inner_instructions_do_not() {
    let tx = load("create_quote_mint");
    let logs: Vec<String> = tx["meta"]["logMessages"].as_array().unwrap().iter().map(|v| v.as_str().unwrap().to_owned()).collect();
    let from_logs = events_from_logs(&logs);
    assert!(from_logs.truncated);
    assert_eq!(from_logs.events.iter().map(|e| e.name()).collect::<Vec<_>>(), ["CreateEvent"]);
    let full = events_from_transaction(&tx).unwrap();
    assert!(full.truncated_logs);
    assert_eq!(full.events.iter().map(|e| e.name()).collect::<Vec<_>>(), ["CreateEvent", "TradeEvent"]);
}

#[test]
fn logs_path_matches_full_path_when_not_truncated() {
    for name in ["buy_direct_v3", "sell_routed", "create_sol"] {
        let tx = load(name);
        let logs: Vec<String> = tx["meta"]["logMessages"].as_array().unwrap().iter().map(|v| v.as_str().unwrap().to_owned()).collect();
        assert_eq!(events_from_logs(&logs).events, events_from_transaction(&tx).unwrap().events, "{name}");
    }
}

fn trades() -> Vec<(String, TradeEvent)> {
    let mut v = Vec::new();
    for name in FIXTURES {
        for e in events_from_transaction(&load(name)).unwrap().events {
            if let PumpEvent::Trade(t) = e {
                v.push((name.to_string(), t));
            }
        }
    }
    v
}

fn fees_of(t: &TradeEvent) -> FeeSchedule {
    FeeSchedule {
        protocol_bps: t.fee_basis_points as u32,
        creator_bps: t.creator_fee_basis_points as u32,
        other_bps: t.tail.as_ref().map(|x| x.cashback_fee_basis_points as u32).unwrap_or(0),
    }
}

#[test]
fn curve_math_reproduces_every_mainnet_trade_exactly() {
    let all = trades();
    assert!(all.len() >= 7, "expected at least 7 trades, got {}", all.len());
    let mut non_sol = 0;
    for (name, t) in all {
        let fees = fees_of(&t);
        if !t.is_sol_quoted() {
            non_sol += 1;
            assert_eq!(t.sol_amount, 0, "{name}: sol fields are zero on quote-mint curves");
        }
        // Quote-denominated view (equals the SOL fields on SOL curves).
        let (q, vq, rq) = (t.quote_amount(), t.virtual_quote_reserves(), t.real_quote_reserves());
        let post = CurveState { virtual_sol_reserves: vq, virtual_token_reserves: t.virtual_token_reserves, real_sol_reserves: rq, real_token_reserves: t.real_token_reserves, complete: t.real_token_reserves == 0 };
        if t.is_buy {
            let mut pre = CurveState { virtual_sol_reserves: vq - q, virtual_token_reserves: t.virtual_token_reserves + t.token_amount, real_sol_reserves: rq - q, real_token_reserves: t.real_token_reserves + t.token_amount, complete: false };
            let quote = if t.ix_name == "buy" {
                pre.quote_buy_exact_tokens(TokenAmount(t.token_amount), fees).unwrap()
            } else {
                // A buyout (curve emptied) means the buyer offered more than it
                // cost; the program caps tokens and recomputes the price.
                let budget = if t.real_token_reserves == 0 { 2 * q + t.trader_fees() } else { q + t.trader_fees() };
                pre.quote_buy(Lamports(budget), fees).unwrap()
            };
            pre.apply_quote(&quote);
            assert_eq!(quote.curve_input.0, q, "{name}: curve input");
            assert_eq!(quote.tokens_out.0, t.token_amount, "{name}: tokens out");
            assert_eq!(quote.fee.0, t.trader_fees(), "{name}: fees");
            assert_eq!(pre, post, "{name}: post-trade reserves");
        } else {
            let mut pre = CurveState { virtual_sol_reserves: vq + q, virtual_token_reserves: t.virtual_token_reserves - t.token_amount, real_sol_reserves: rq + q, real_token_reserves: t.real_token_reserves - t.token_amount, complete: false };
            let quote = pre.apply_sell(TokenAmount(t.token_amount), fees).unwrap();
            assert_eq!(quote.gross.0, q, "{name}: gross");
            assert_eq!(quote.fee.0, t.trader_fees(), "{name}: fees");
            assert_eq!(quote.sol_out.0, q - t.trader_fees(), "{name}: net");
            assert_eq!(pre, post, "{name}: post-trade reserves");
        }
    }
    assert!(non_sol >= 2, "fixtures should include quote-mint trades");
}

#[test]
fn global_account_matches_curve_constants() {
    let acct = load("global_account");
    let data = base64::engine::general_purpose::STANDARD.decode(acct["data"][0].as_str().unwrap()).unwrap();
    let g = decode_global(&data).unwrap();
    assert!(g.initialized);
    assert_eq!(g.initial_virtual_sol_reserves, INITIAL_VIRTUAL_SOL);
    assert_eq!(g.initial_virtual_token_reserves, INITIAL_VIRTUAL_TOKENS);
    assert_eq!(g.initial_real_token_reserves, INITIAL_REAL_TOKENS);
    assert_eq!(g.token_total_supply, TOKEN_TOTAL_SUPPLY);
    assert_eq!(g.fee_basis_points, 95);
    assert_eq!(g.creator_fee_basis_points, 5);
}

#[test]
fn create_events_flag_quote_mint_and_token_program() {
    let sol = events_from_transaction(&load("create_sol")).unwrap();
    let PumpEvent::Create(c) = &sol.events[0] else { panic!() };
    assert!(c.is_sol_quoted());
    let other = events_from_transaction(&load("create_quote_mint")).unwrap();
    let PumpEvent::Create(q) = &other.events[0] else { panic!() };
    assert!(!q.is_sol_quoted(), "non-SOL quoted curve must be detectable");
    assert_eq!(q.token_program().unwrap().to_string(), pf_solana::pump::TOKEN_2022_PROGRAM);
}

#[test]
fn real_pump_mints_are_token2022_with_metadata_only() {
    use pf_solana::token::decode_mint;
    for (name, expect_name) in [("mint_token2022_a", "dutt and dutter"), ("mint_token2022_b", "Melmin"), ("mint_token2022_c", "")] {
        let acct = load(name);
        let v = &acct["result"]["value"];
        assert_eq!(v["owner"].as_str().unwrap(), pf_solana::pump::TOKEN_2022_PROGRAM);
        let data = base64::engine::general_purpose::STANDARD.decode(v["data"][0].as_str().unwrap()).unwrap();
        let m = decode_mint(&data, true).unwrap();
        assert_eq!(m.extensions, vec![18, 19], "{name}");
        assert!(m.mint_authority.is_none() && m.freeze_authority.is_none(), "{name}");
        assert_eq!(m.decimals, 6);
        assert!(m.red_flags().is_empty(), "{name}: {:?}", m.red_flags());
        if !expect_name.is_empty() {
            assert_eq!(m.metadata_name.as_deref(), Some(expect_name));
        }
    }
}
