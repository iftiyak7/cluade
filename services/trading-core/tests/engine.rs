//! End-to-end engine behaviour on a deterministic synthetic market.

use pf_config::TradingConfig;
use pf_events::{Envelope, Event, TokenDiscovered, TradeObserved};
use pf_market::SOL_QUOTE;
use pf_solana::curve::{CurveState, FeeSchedule};
use pf_solana::token::MintInfo;
use pf_trading_core::engine::{Effect, Engine};
use pf_types::{Lamports, Side, TokenAmount, TradingMode};
use std::collections::BTreeMap;

const SOL: u64 = 1_000_000_000;
const FEES: FeeSchedule = FeeSchedule::new(95, 30);

fn cfg() -> TradingConfig {
    let mut c = TradingConfig::default();
    c.switches.master = true;
    c.switches.auto_buy = true;
    c
}

fn clean_mint() -> MintInfo {
    MintInfo { token_2022: true, mint_authority: None, freeze_authority: None, supply: 1, decimals: 6, is_initialized: true, extensions: vec![18, 19], metadata_name: None }
}

struct Sim {
    e: Engine,
    curve: CurveState,
    slot: u64,
    bt: i64,
    held: BTreeMap<String, u64>,
    mint_info: MintInfo,
    logs: Vec<serde_json::Value>,
    fetches: u32,
}

impl Sim {
    fn new(cfg: TradingConfig) -> Self {
        Self { e: Engine::new(cfg).unwrap(), curve: CurveState::fresh(), slot: 100, bt: 1_000, held: BTreeMap::new(), mint_info: clean_mint(), logs: vec![], fetches: 0 }
    }
    fn wall(&self) -> i64 {
        self.bt * 1000
    }
    fn feed(&mut self, ev: Event) {
        let env = Envelope::new("test", self.wall(), None, ev);
        let (w, s) = (self.wall(), self.slot);
        self.e.handle(&env, w, s);
        self.pump_effects();
    }
    fn pump_effects(&mut self) {
        for eff in self.e.drain_effects() {
            match eff {
                Effect::FetchMint(m) => {
                    self.fetches += 1;
                    let info = self.mint_info.clone();
                    self.e.on_mint(&m, Ok(info));
                }
                Effect::Log(v) => self.logs.push(v),
                Effect::Db(_) => {}
            }
        }
    }
    fn create(&mut self) {
        self.feed(Event::TokenDiscovered(TokenDiscovered {
            mint: "M".into(), bonding_curve: "BC".into(), creator: "CR".into(), name: "n".into(), symbol: "s".into(), uri: "u".into(),
            slot: self.slot, signature: "create".into(), quote_mint: SOL_QUOTE.into(), token_program: None, mayhem_mode: false,
            creator_fee_bps: Some(30), block_time: Some(self.bt), received_at_us: self.wall() * 1000, decoded_at_us: self.wall() * 1000,
        }));
    }
    fn trade(&mut self, who: &str, buy: bool, amount: u64) {
        let (q, tok) = if buy {
            let r = self.curve.apply_buy(Lamports(amount), FEES).unwrap();
            *self.held.entry(who.into()).or_default() += r.tokens_out.0;
            (r.curve_input.0, r.tokens_out.0)
        } else {
            let r = self.curve.apply_sell(TokenAmount(amount), FEES).unwrap();
            *self.held.get_mut(who).unwrap() -= amount;
            (r.gross.0, amount)
        };
        let c = self.curve;
        let ev = Event::TradeObserved(TradeObserved {
            mint: "M".into(), signature: format!("{who}-{}-{}", self.slot, q), trader: who.into(), side: if buy { Side::Buy } else { Side::Sell },
            quote_amount: q, token_amount: TokenAmount(tok), virtual_quote_reserves: c.virtual_sol_reserves, virtual_token_reserves: c.virtual_token_reserves,
            real_quote_reserves: c.real_sol_reserves, real_token_reserves: c.real_token_reserves, quote_mint: SOL_QUOTE.into(),
            fee_bps: 95, creator_fee_bps: 30, fees: 0, ix_name: "buy_exact_quote_in".into(), slot: self.slot, block_time: Some(self.bt),
        });
        self.feed(ev);
    }
    fn step(&mut self) {
        self.slot += 1;
        self.bt += 1;
        let (w, s) = (self.wall(), self.slot);
        self.e.tick(w, s);
        self.pump_effects();
    }
    /// 30 distinct buyers over 30 s, then one sell so sellability is observed.
    fn warm_up(&mut self) {
        self.create();
        for i in 0..30 {
            self.step();
            self.trade(&format!("W{i}"), true, 150_000_000);
        }
    }
    fn kinds(&self) -> Vec<String> {
        self.logs.iter().map(|l| l["kind"].as_str().unwrap().to_string()).collect()
    }
}

#[test]
fn refuses_live_configuration() {
    let mut c = cfg();
    c.mode = TradingMode::Live;
    c.live_trading_enabled = true;
    assert!(Engine::new(c).is_err());
}

#[test]
fn enters_after_landing_latency_and_takes_profit_on_pump() {
    let mut s = Sim::new(cfg());
    s.warm_up();
    assert_eq!(s.fetches, 1, "mint inspected exactly once");
    assert!(s.kinds().contains(&"buy_submitted".to_string()), "{:?}", s.e.counters);
    assert!(s.kinds().contains(&"buy_filled".to_string()), "{:?}", s.kinds());
    // pump: big buyers push the price up
    for i in 0..25 {
        s.step();
        s.trade(&format!("P{i}"), true, 2 * SOL);
    }
    // dump: everyone exits
    let sellers: Vec<(String, u64)> = s.held.iter().filter(|(k, _)| k.starts_with('P') || k.starts_with('W')).map(|(k, v)| (k.clone(), *v)).collect();
    for (who, amt) in sellers {
        s.step();
        if amt > 0 {
            s.trade(&who, false, amt);
        }
    }
    for _ in 0..10 {
        s.step();
    }
    let r = s.e.report();
    assert_eq!(r.open_positions, 0, "{:#?}", r);
    assert_eq!(r.closed.trades, 1);
    assert!(r.closed.net_pnl > 0, "pump then dump should still have taken profit: {:#?}", r);
    assert!(r.counters.keys().any(|k| k.starts_with("exit: TakeProfit")), "{:?}", r.counters);
    assert_eq!(r.accounting_check_lamports, 0, "wallet must reconcile to the lamport");
    assert_eq!(r.rent_locked_sol, 0.0, "rent refunded on close");
}

#[test]
fn buy_fails_when_price_runs_away_before_landing() {
    let mut s = Sim::new(cfg());
    s.create();
    for i in 0..30 {
        s.step();
        s.trade(&format!("W{i}"), true, 150_000_000);
        if s.kinds().contains(&"buy_submitted".to_string()) {
            break;
        }
    }
    assert!(s.kinds().contains(&"buy_submitted".to_string()));
    // a whale lands first in the next slots
    s.step();
    s.trade("WHALE", true, 30 * SOL);
    s.step();
    s.step();
    s.trade("X", true, 10_000_000);
    let r = s.e.report();
    assert_eq!(r.counters.get("buy_failed").copied(), Some(1), "{:?}", r.counters);
    assert!(r.failed_tx_fees_sol > 0.0, "failed transactions still cost the network fee");
    assert_eq!(r.open_positions, 0);
    assert_eq!(r.accounting_check_lamports, 0);
}

#[test]
fn creator_dump_triggers_suspicious_exit() {
    let mut s = Sim::new(cfg());
    s.create();
    s.step();
    s.trade("CR", true, 2 * SOL); // creator dev-buy
    for i in 0..30 {
        s.step();
        s.trade(&format!("W{i}"), true, 150_000_000);
    }
    assert!(s.kinds().contains(&"buy_filled".to_string()), "{:?}", s.e.counters);
    let amt = s.held["CR"];
    s.step();
    s.trade("CR", false, amt);
    for _ in 0..5 {
        s.step();
        s.trade("Y", true, 10_000_000);
    }
    let r = s.e.report();
    assert_eq!(r.closed.trades, 1, "{:#?}", r);
    assert!(r.counters.keys().any(|k| k.contains("SuspiciousActivity") || k.contains("HardStopLoss")), "{:?}", r.counters);
    assert_eq!(r.accounting_check_lamports, 0);
}

#[test]
fn rejects_token_with_dangerous_extension() {
    let mut s = Sim::new(cfg());
    s.mint_info.extensions.push(14); // TransferHook
    s.warm_up();
    let r = s.e.report();
    assert_eq!(r.counters.get("buy_submitted"), None);
    assert!(r.counters.keys().any(|k| k.starts_with("reject: token extension")), "{:?}", r.counters);
}

#[test]
fn restart_restores_position_and_keeps_accounting() {
    let mut s = Sim::new(cfg());
    s.warm_up();
    assert!(s.kinds().contains(&"buy_filled".to_string()));
    let before = s.e.report();
    let snap = s.e.snapshot();
    let json = serde_json::to_string(&snap).unwrap();
    let restored = Engine::restore(cfg(), serde_json::from_str(&json).unwrap()).unwrap();
    s.e = restored;
    let after = s.e.report();
    assert_eq!(after.open_positions, 1);
    assert_eq!(after.wallet_sol, before.wallet_sol);
    assert_eq!(after.accounting_check_lamports, 0);
    // the restored position keeps being managed: crash the price
    let sellers: Vec<(String, u64)> = s.held.iter().map(|(k, v)| (k.clone(), *v)).collect();
    for (who, amt) in sellers {
        s.step();
        s.trade(&who, false, amt);
    }
    for _ in 0..10 {
        s.step();
    }
    let r = s.e.report();
    assert_eq!(r.open_positions, 0, "restored position must still be managed to a close: {:#?}", r);
    assert_eq!(r.closed.trades, 1);
    assert_eq!(r.accounting_check_lamports, 0);
}

#[test]
fn migration_while_holding_is_closed_and_labelled() {
    let mut s = Sim::new(cfg());
    s.warm_up();
    assert!(s.kinds().contains(&"buy_filled".to_string()));
    s.feed(Event::CurveCompleted { mint: "M".into(), slot: s.slot, signature: "c".into(), migrated_pool: Some("POOL".into()) });
    let r = s.e.report();
    assert_eq!(r.open_positions, 0);
    assert_eq!(r.exits_by_reason.get("MigratedApproximated"), Some(&1));
    assert_eq!(r.accounting_check_lamports, 0);
}
