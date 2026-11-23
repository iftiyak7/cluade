//! The trading engine: a deterministic state machine.
//!
//! Inputs: discovery envelopes, mint-account results, timer ticks.
//! Outputs: effects (`FetchMint`, `Db`, `Log`) drained by the driver.
//!
//! Order of authority on every buy: strategy proposes → risk engine
//! decides and sizes → paper broker fills (or fails) after modelled
//! landing latency → position manager owns the lifecycle → risk engine
//! books the result. Exits follow the same path in reverse.
//!
//! PAPER ONLY. `Engine::new` refuses a configuration that allows live orders.

use crate::paper::{FillResult, PaperOrder, PaperSide};
use crate::store::DbOp;
use anyhow::{bail, Result};
use pf_analytics::{summarize, ClosedTrade, Summary};
use pf_config::TradingConfig;
use pf_events::{Envelope, Event};
use pf_intel::{assess, Assessment};
use pf_market::{MarketBook, TokenSnapshot, TokenState};
use pf_position::{ExitOrder, Position, Tick};
use pf_risk::{Breaker, BuyRequest, Portfolio, RiskState, SellFill, SellRequest};
use pf_solana::token::MintInfo;
use pf_strategy::{evaluate_entry, EntryDecision};
use pf_types::{Bps, Lamports, TokenAmount, TradingMode};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet};
use uuid::Uuid;

pub const SNAPSHOT_VERSION: u32 = 1;

#[derive(Debug, Clone, PartialEq)]
pub enum Effect {
    FetchMint(String),
    Db(Box<DbOp>),
    Log(serde_json::Value),
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Held {
    pub position: Position,
    pub entry_intent: String,
    pub strategy: String,
    pub fees_paid: u64,
    pub creator_sold_at_entry: bool,
    pub last_value: u64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct EngineSnapshot {
    pub version: u32,
    pub wallet: u64,
    pub starting_balance: u64,
    pub rent_locked: u64,
    pub failed_tx_fees: u64,
    pub risk: RiskState,
    pub positions: Vec<Held>,
    pub tokens: Vec<TokenSnapshot>,
    pub closed: Vec<ClosedTrade>,
    pub rejected: Vec<String>,
    pub chain_ms: i64,
    pub counters: BTreeMap<String, u64>,
}

#[derive(Debug, Clone, Serialize)]
pub struct Report {
    pub mode: &'static str,
    pub starting_balance_sol: f64,
    pub wallet_sol: f64,
    pub equity_sol: f64,
    pub open_positions: usize,
    pub open_cost_sol: f64,
    pub open_value_sol: f64,
    pub rent_locked_sol: f64,
    pub failed_tx_fees_sol: f64,
    pub closed: Summary,
    pub breakers: Vec<String>,
    pub counters: BTreeMap<String, u64>,
    pub exits_by_reason: BTreeMap<String, u64>,
    pub accounting_check_lamports: i64,
}

pub struct Engine {
    cfg: TradingConfig,
    book: MarketBook,
    mints: HashMap<String, MintInfo>,
    mint_requested: HashSet<String>,
    rejected: HashSet<String>,
    last_eval: HashMap<String, i64>,
    positions: BTreeMap<String, Held>,
    pending: Vec<PaperOrder>,
    risk: RiskState,
    wallet: u64,
    starting_balance: u64,
    rent_locked: u64,
    failed_tx_fees: u64,
    closed: Vec<ClosedTrade>,
    chain_ms: i64,
    max_slot: u64,
    feed_lag_ms: f64,
    last_snapshot_ms: i64,
    effects: Vec<Effect>,
    pub counters: BTreeMap<String, u64>,
}

fn sol(l: u64) -> f64 {
    l as f64 / 1e9
}

/// Group reasons for counters: "age 12s < 20s" → "age <",
/// "5 trades < 15" → "trades <". Addresses are kept (quote mints matter).
fn reason_key(r: &str) -> String {
    let head = r.split(':').next().unwrap_or(r);
    // drop numeric words ("16s", "0.5", "30.00%"); keep long ones (addresses)
    let words: Vec<&str> = head.split_whitespace().filter(|w| w.len() > 16 || !w.chars().any(|c| c.is_ascii_digit())).collect();
    let k = words.join(" ");
    let k = k.trim_matches(|c: char| !c.is_alphanumeric());
    k.chars().take(48).collect()
}

impl Engine {
    pub fn new(cfg: TradingConfig) -> Result<Self> {
        if let Err(errs) = cfg.validate() {
            bail!("invalid configuration: {:?}", errs);
        }
        if cfg.live_orders_allowed() || cfg.mode == TradingMode::Live {
            bail!("live execution is not implemented; this engine runs in paper mode only");
        }
        let start = cfg.paper.starting_balance.0;
        Ok(Self {
            risk: RiskState::new(TradingMode::Paper, 0, Lamports(start)),
            cfg,
            book: MarketBook::default(),
            mints: HashMap::new(),
            mint_requested: HashSet::new(),
            rejected: HashSet::new(),
            last_eval: HashMap::new(),
            positions: BTreeMap::new(),
            pending: Vec::new(),
            wallet: start,
            starting_balance: start,
            rent_locked: 0,
            failed_tx_fees: 0,
            closed: Vec::new(),
            chain_ms: 0,
            max_slot: 0,
            feed_lag_ms: 0.0,
            last_snapshot_ms: 0,
            effects: Vec::new(),
            counters: BTreeMap::new(),
        })
    }

    /// Resume from a snapshot. In-flight paper orders did not survive the
    /// restart: pending entries are failed (reservation released) and
    /// pending exits are failed so they are re-evaluated.
    pub fn restore(cfg: TradingConfig, snap: EngineSnapshot) -> Result<Self> {
        if snap.version != SNAPSHOT_VERSION {
            bail!("snapshot version {} unsupported", snap.version);
        }
        let mut e = Self::new(cfg)?;
        e.wallet = snap.wallet;
        e.starting_balance = snap.starting_balance;
        e.rent_locked = snap.rent_locked;
        e.failed_tx_fees = snap.failed_tx_fees;
        e.risk = snap.risk;
        e.closed = snap.closed;
        e.rejected = snap.rejected.into_iter().collect();
        e.chain_ms = snap.chain_ms;
        e.counters = snap.counters;
        for t in &snap.tokens {
            e.book.restore(t);
        }
        for mut h in snap.positions {
            let mint = h.position.mint.clone();
            if h.position.state == pf_position::PositionState::EntryPending {
                let _ = h.position.on_entry_failed(e.chain_ms, "restart before fill");
                let cfg = e.cfg.clone();
                e.risk.on_buy_failed(&cfg, &h.entry_intent);
                e.inc("restore_entry_dropped");
                continue;
            }
            for intent in h.position.pending_exit_intents() {
                let _ = h.position.on_exit_failed(&intent, e.chain_ms);
                e.inc("restore_exit_reset");
            }
            e.positions.insert(mint, h);
        }
        e.inc("restored");
        Ok(e)
    }

    pub fn snapshot(&self) -> EngineSnapshot {
        EngineSnapshot {
            version: SNAPSHOT_VERSION,
            wallet: self.wallet,
            starting_balance: self.starting_balance,
            rent_locked: self.rent_locked,
            failed_tx_fees: self.failed_tx_fees,
            risk: self.risk.clone(),
            positions: self.positions.values().cloned().collect(),
            tokens: self.positions.keys().filter_map(|m| self.book.tokens.get(m)).map(TokenState::snapshot).collect(),
            closed: self.closed.clone(),
            rejected: self.rejected.iter().cloned().collect(),
            chain_ms: self.chain_ms,
            counters: self.counters.clone(),
        }
    }

    pub fn drain_effects(&mut self) -> Vec<Effect> {
        std::mem::take(&mut self.effects)
    }

    fn inc(&mut self, k: &str) {
        *self.counters.entry(k.to_string()).or_default() += 1;
    }

    fn db(&mut self, op: DbOp) {
        self.effects.push(Effect::Db(Box::new(op)));
    }

    fn log(&mut self, kind: &str, mint: &str, data: serde_json::Value) {
        self.effects.push(Effect::Log(serde_json::json!({"t": self.chain_ms, "kind": kind, "mint": mint, "data": data})));
    }

    fn now_est(&self, wall_ms: i64) -> i64 {
        self.chain_ms.max(wall_ms - self.feed_lag_ms as i64)
    }

    fn observe_clock(&mut self, block_time: Option<i64>, slot: u64, wall_ms: i64) {
        if let Some(bt) = block_time {
            let ms = bt * 1000;
            self.chain_ms = self.chain_ms.max(ms);
            let lag = (wall_ms - ms).max(0) as f64;
            self.feed_lag_ms = if self.feed_lag_ms == 0.0 { lag } else { 0.95 * self.feed_lag_ms + 0.05 * lag };
        }
        self.max_slot = self.max_slot.max(slot);
    }

    // ------------------------------------------------------------ inputs

    pub fn handle(&mut self, env: &Envelope, wall_ms: i64, tip_slot: u64) {
        let tip = tip_slot.max(self.max_slot);
        match &env.event {
            Event::TokenDiscovered(d) => {
                self.observe_clock(d.block_time, d.slot, wall_ms);
                self.inc("tokens_discovered");
                let t = self.book.on_create(d);
                let op = DbOp::Token {
                    mint: d.mint.clone(),
                    symbol: d.symbol.clone(),
                    name: d.name.clone(),
                    uri: d.uri.clone(),
                    creator: d.creator.clone(),
                    bonding_curve: d.bonding_curve.clone(),
                    created_slot: d.slot,
                    created_ms: t.created_ms,
                    quote_mint: d.quote_mint.clone(),
                    token_program: d.token_program.clone(),
                    mayhem: d.mayhem_mode,
                    discovery_latency_us: d.decoded_at_us - d.received_at_us,
                };
                self.db(op);
                if !self.cfg.entry.new_launches {
                    self.rejected.insert(d.mint.clone());
                }
            }
            Event::TradeObserved(t) => {
                self.observe_clock(t.block_time, t.slot, wall_ms);
                if self.book.on_trade(t).is_none() {
                    return;
                }
                self.inc("trades_tracked");
                self.db(DbOp::Trade {
                    signature: t.signature.clone(),
                    mint: t.mint.clone(),
                    trader: t.trader.clone(),
                    is_buy: t.side == pf_types::Side::Buy,
                    quote_amount: t.quote_amount,
                    token_amount: t.token_amount.0,
                    virtual_quote_reserves: t.virtual_quote_reserves,
                    virtual_token_reserves: t.virtual_token_reserves,
                    slot: t.slot,
                    block_time: t.block_time,
                    quote_mint: t.quote_mint.clone(),
                    fees: t.fees,
                    ix_name: t.ix_name.clone(),
                });
                let now = self.chain_ms;
                let mint = t.mint.clone();
                self.process_fills(Some(&mint), t.slot, now);
                if self.positions.contains_key(&mint) {
                    self.evaluate_position(&mint, now, tip);
                } else {
                    self.maybe_evaluate_entry(&mint, now, tip);
                }
            }
            Event::CurveCompleted { mint, migrated_pool, slot, .. } => {
                self.observe_clock(None, *slot, wall_ms);
                self.book.on_complete(mint, migrated_pool.clone());
                if self.book.tokens.contains_key(mint) {
                    self.db(DbOp::CurveComplete { mint: mint.clone(), at_ms: self.chain_ms });
                }
                let m = mint.clone();
                self.process_fills(Some(&m), *slot, self.chain_ms);
                if self.positions.contains_key(&m) {
                    self.close_migrated(&m, self.chain_ms);
                }
            }
            _ => {}
        }
    }

    pub fn on_mint(&mut self, mint: &str, info: std::result::Result<MintInfo, String>) {
        match info {
            Ok(m) => {
                self.inc("mint_inspected");
                if !m.red_flags().is_empty() {
                    self.inc("mint_red_flags");
                }
                self.mints.insert(mint.to_string(), m);
            }
            Err(e) => {
                // Allow a later retry; never enter without inspection.
                self.inc("mint_fetch_error");
                self.mint_requested.remove(mint);
                self.log("mint_fetch_error", mint, serde_json::json!({"error": e}));
            }
        }
    }

    /// Periodic driver call (≥ 1 Hz): fill timeouts, max-hold exits,
    /// portfolio breakers, memory eviction, snapshots.
    pub fn tick(&mut self, wall_ms: i64, tip_slot: u64) {
        let now = self.now_est(wall_ms);
        let tip = tip_slot.max(self.max_slot);
        self.process_fills(None, 0, now);
        let mints: Vec<String> = self.positions.keys().cloned().collect();
        for m in &mints {
            self.evaluate_position(m, now, tip);
        }
        let p = self.portfolio();
        let cfg = self.cfg.clone();
        self.risk.observe_portfolio(&cfg, &p, now);
        let keep: HashSet<String> = self.positions.keys().cloned().chain(self.pending.iter().map(|o| o.mint.clone())).collect();
        let max_age = (self.cfg.entry.max_age_secs as i64 + 600) * 1000;
        let evicted = self.book.evict(now, max_age, |m| keep.contains(m));
        if evicted > 0 {
            self.book.tokens.keys().for_each(|_| {});
            let live: HashSet<String> = self.book.tokens.keys().cloned().collect();
            self.mints.retain(|m, _| live.contains(m));
            self.last_eval.retain(|m, _| live.contains(m));
            self.mint_requested.retain(|m| live.contains(m));
            self.rejected.retain(|m| live.contains(m));
        }
        if now - self.last_snapshot_ms >= 10_000 {
            self.emit_snapshot(now);
        }
    }

    fn emit_snapshot(&mut self, now: i64) {
        self.last_snapshot_ms = now;
        if let Ok(v) = serde_json::to_value(self.snapshot()) {
            self.db(DbOp::Snapshot(v));
        }
    }

    // ------------------------------------------------------------ entries

    fn facts_and_assessment(&self, t: &TokenState, now: i64) -> (pf_intel::TokenFacts, Assessment) {
        let mut facts = t.facts();
        let (launches, dumps) = self.book.creator_history(&t.creator, &t.mint);
        facts.creator_prior_launches = launches;
        facts.creator_prior_rugs = dumps;
        if let Some(m) = self.mints.get(&t.mint) {
            facts.mint_authority_present = m.mint_authority.is_some();
            facts.freeze_authority_present = m.freeze_authority.is_some();
            facts.dangerous_extensions = m.red_flags().into_iter().filter(|r| r.starts_with("token extension")).collect();
        }
        facts.mint_blacklisted = self.cfg.entry.token_blacklist.contains(&t.mint);
        facts.creator_blacklisted = self.cfg.entry.creator_blacklist.contains(&t.creator);
        let stats = t.stats(now, self.cfg.sizing.base_size);
        let a = assess(&self.cfg, &facts, &stats);
        (facts, a)
    }

    fn maybe_evaluate_entry(&mut self, mint: &str, now: i64, tip: u64) {
        if self.rejected.contains(mint) || self.pending.iter().any(|o| o.mint == mint) {
            return;
        }
        if self.last_eval.get(mint).is_some_and(|t| now - t < self.cfg.entry.reevaluate_every_ms as i64) {
            return;
        }
        self.last_eval.insert(mint.to_string(), now);
        let Some(t) = self.book.tokens.get(mint) else { return };
        // Fetch the mint account once the token is close to eligible.
        let sniper = self.cfg.entry.buy_mode == pf_config::BuyMode::InstantSniper;
        if !self.mint_requested.contains(mint) && (sniper || t.age_secs(now) * 2 >= self.cfg.entry.min_age_secs) && t.is_sol_quoted() && (!t.mayhem || self.cfg.entry.allow_mayhem) {
            self.mint_requested.insert(mint.to_string());
            self.effects.push(Effect::FetchMint(mint.to_string()));
        }
        let (_, a) = self.facts_and_assessment(t, now);
        let decision = evaluate_entry(&self.cfg, t, self.mints.get(mint), &a, now);
        match decision {
            EntryDecision::Reject { reasons } => {
                self.rejected.insert(mint.to_string());
                self.inc("entry_rejected");
                let k = format!("reject: {}", reason_key(&reasons[0]));
                self.inc(&k);
                self.score_op(mint, &a);
                self.log("reject", mint, serde_json::json!({"reasons": reasons}));
            }
            EntryDecision::Wait { reasons } => {
                self.inc("entry_wait");
                let k = format!("wait: {}", reason_key(&reasons[0]));
                self.inc(&k);
            }
            EntryDecision::Enter { size, risk_class, requires_approval, reasons } => {
                self.score_op(mint, &a);
                if requires_approval {
                    self.rejected.insert(mint.to_string());
                    self.inc("manual_approval_required");
                    self.log("manual_intent", mint, serde_json::json!({"size": size.0, "reasons": reasons}));
                    return;
                }
                self.submit_buy(mint, size, risk_class, now, tip, &a);
            }
        }
    }

    fn score_op(&mut self, mint: &str, a: &Assessment) {
        let op = DbOp::Score {
            mint: mint.to_string(),
            safety: a.safety,
            momentum: a.momentum,
            liquidity: a.liquidity,
            opportunity: a.opportunity,
            hard_rejects: a.hard_rejects.clone(),
            factors: serde_json::to_value(&a.factors).unwrap_or_default(),
        };
        self.db(op);
    }

    fn portfolio(&self) -> Portfolio {
        let (mut v, mut u) = (0u64, 0i64);
        for (m, h) in &self.positions {
            if let Some(t) = self.book.tokens.get(m) {
                let val = final_value(t, h.position.tokens_held).0;
                v += val;
                u += h.position.unrealized_pnl(Lamports(val));
            }
        }
        Portfolio { wallet_balance: Lamports(self.wallet), open_value: Lamports(v), unrealized_pnl: u }
    }

    fn submit_buy(&mut self, mint: &str, size: Lamports, risk_class: pf_types::RiskClass, now: i64, tip: u64, a: &Assessment) {
        let Some(t) = self.book.tokens.get(mint) else { return };
        let (curve, fees, creator_sold) = (t.curve, t.fees, t.creator_sold);
        let Ok(quote) = curve.quote_buy(size, fees) else { return };
        let intent_id = Uuid::new_v4().to_string();
        let req = BuyRequest {
            intent_id: intent_id.clone(),
            mint: mint.to_string(),
            mode: TradingMode::Paper,
            size,
            risk_class,
            max_slippage: self.cfg.risk.max_slippage,
            price_impact: quote.price_impact,
            est_tx_fee: self.cfg.paper.network_fee,
            market_data_age_ms: self.feed_lag_ms as u64,
        };
        // The token-account rent must be available on top of the trade.
        let mut p = self.portfolio();
        p.wallet_balance = p.wallet_balance.saturating_sub(self.cfg.paper.token_account_rent);
        let cfg = self.cfg.clone();
        let d = self.risk.evaluate_buy(&cfg, &req, &p, now);
        let mode = format!("{:?}", self.cfg.entry.buy_mode).to_lowercase();
        self.db(DbOp::Intent { intent_id: intent_id.clone(), mint: mint.to_string(), strategy: "default".into(), buy_mode: mode, size: size.0, max_slippage_bps: req.max_slippage.0, created_ms: now, expires_ms: now + self.cfg.paper.fill_timeout_ms as i64 });
        let approved = d.approved_size();
        let reasons = match &d {
            pf_risk::Decision::Approved { notes, .. } => notes.clone(),
            pf_risk::Decision::Denied { reasons } => reasons.clone(),
        };
        self.db(DbOp::RiskDecision { intent_id: intent_id.clone(), side: "buy".into(), approved: approved.is_some(), size: approved.map(|s| s.0), reasons: reasons.clone() });
        let Some(size) = approved else {
            self.inc("risk_denied_buy");
            if let Some(r) = reasons.first() {
                let k = format!("risk: {}", reason_key(r));
                self.inc(&k);
            }
            return;
        };
        let Ok(q) = curve.quote_buy(size, fees) else { return };
        let position_id = Uuid::new_v4().to_string();
        let order = PaperOrder {
            order_id: Uuid::new_v4().to_string(),
            intent_id: intent_id.clone(),
            mint: mint.to_string(),
            position_id: position_id.clone(),
            side: PaperSide::Buy { budget: size, quoted_tokens: q.tokens_out.0 },
            max_slippage: self.cfg.risk.max_slippage,
            target_slot: tip + self.cfg.paper.landing_slots,
            deadline_ms: now + self.cfg.paper.fill_timeout_ms as i64,
        };
        let held = Held { position: Position::new(position_id.clone(), mint, now), entry_intent: intent_id.clone(), strategy: "default".into(), fees_paid: 0, creator_sold_at_entry: creator_sold, last_value: 0 };
        self.db(DbOp::Order { order_id: order.order_id.clone(), intent_id, side: "buy".into(), mint: mint.to_string(), position_id: Some(position_id), requested_lamports: Some(size.0), requested_tokens: None, min_out: order.min_out(), status: "submitted".into(), failure_reason: None });
        self.log("buy_submitted", mint, serde_json::json!({"size": size.0, "quoted_tokens": q.tokens_out.0, "safety": a.safety, "momentum": a.momentum, "liquidity": a.liquidity, "opportunity": a.opportunity, "risk_class": format!("{risk_class:?}")}));
        self.positions.insert(mint.to_string(), held);
        self.pending.push(order);
        self.inc("buy_submitted");
    }

    // ------------------------------------------------------------ fills

    fn process_fills(&mut self, mint: Option<&str>, slot: u64, now: i64) {
        let mut i = 0;
        while i < self.pending.len() {
            let o = &self.pending[i];
            let relevant = mint.is_none_or(|m| m == o.mint);
            let ready = relevant && (if mint.is_some() { o.ready(slot, now) } else { now >= o.deadline_ms });
            if !ready {
                i += 1;
                continue;
            }
            let o = self.pending.remove(i);
            let Some(t) = self.book.tokens.get(&o.mint) else {
                continue;
            };
            let result = o.fill(t);
            let real_reserves = t.curve.real_sol_reserves;
            let creator_sold = t.creator_sold;
            self.apply_fill(o, result, slot, now, real_reserves, creator_sold);
        }
    }

    fn apply_fill(&mut self, o: PaperOrder, result: FillResult, slot: u64, now: i64, real_reserves: u64, creator_sold: bool) {
        let fee = self.cfg.paper.network_fee.0;
        let rent = self.cfg.paper.token_account_rent.0;
        let cfg = self.cfg.clone();
        match (&o.side, result) {
            (PaperSide::Buy { .. }, FillResult::Bought { tokens, sol_cost, curve_fees, impact }) => {
                let cost = sol_cost.0 + fee;
                self.wallet = self.wallet.saturating_sub(cost + rent);
                self.rent_locked += rent;
                if let Some(h) = self.positions.get_mut(&o.mint) {
                    let _ = h.position.on_entry_filled(Lamports(cost), tokens, Lamports(real_reserves), now);
                    h.fees_paid += curve_fees.0 + fee;
                    h.creator_sold_at_entry = creator_sold;
                }
                self.risk.on_buy_filled(&o.intent_id, Lamports(cost), Lamports(curve_fees.0 + fee));
                self.inc("buy_filled");
                self.db(DbOp::Order { order_id: o.order_id.clone(), intent_id: o.intent_id.clone(), side: "buy".into(), mint: o.mint.clone(), position_id: Some(o.position_id.clone()), requested_lamports: None, requested_tokens: None, min_out: o.min_out(), status: "confirmed".into(), failure_reason: None });
                self.db(DbOp::PaperFill { order_id: o.order_id.clone(), slot, at_ms: now, sol_delta: -(cost as i64) - rent as i64, token_delta: tokens.0 as i64, curve_fees: curve_fees.0, network_fee: fee, price_impact_bps: impact.0 });
                self.log("buy_filled", &o.mint, serde_json::json!({"cost": cost, "tokens": tokens.0, "impact_bps": impact.0}));
                self.position_op(&o.mint, now);
            }
            (PaperSide::Buy { .. }, FillResult::Failed { reason }) => {
                self.wallet = self.wallet.saturating_sub(fee);
                self.failed_tx_fees += fee;
                if let Some(mut h) = self.positions.remove(&o.mint) {
                    let _ = h.position.on_entry_failed(now, &reason);
                }
                self.risk.on_buy_failed(&cfg, &o.intent_id);
                self.inc("buy_failed");
                let k = format!("buy_failed: {}", reason_key(&reason));
                self.inc(&k);
                self.db(DbOp::Order { order_id: o.order_id.clone(), intent_id: o.intent_id.clone(), side: "buy".into(), mint: o.mint.clone(), position_id: Some(o.position_id.clone()), requested_lamports: None, requested_tokens: None, min_out: o.min_out(), status: "failed".into(), failure_reason: Some(reason.clone()) });
                self.log("buy_failed", &o.mint, serde_json::json!({"reason": reason}));
            }
            (PaperSide::Sell { tokens, exit_intent, .. }, FillResult::Sold { sol_out, curve_fees, impact }) => {
                let proceeds = sol_out.0.saturating_sub(fee);
                self.wallet += proceeds;
                let Some(h) = self.positions.get_mut(&o.mint) else { return };
                let Ok(out) = h.position.on_exit_filled(exit_intent, *tokens, Lamports(proceeds), now) else { return };
                h.fees_paid += curve_fees.0 + fee;
                let fees_paid = h.fees_paid;
                self.risk.on_sell_filled(&cfg, &SellFill { mint: o.mint.clone(), cost_released: out.cost_released, proceeds: Lamports(proceeds), fees: Lamports(curve_fees.0 + fee), position_closed: out.closed, position_pnl: out.position_pnl });
                self.inc("sell_filled");
                self.db(DbOp::Order { order_id: o.order_id.clone(), intent_id: o.intent_id.clone(), side: "sell".into(), mint: o.mint.clone(), position_id: Some(o.position_id.clone()), requested_lamports: None, requested_tokens: Some(tokens.0), min_out: o.min_out(), status: "confirmed".into(), failure_reason: None });
                self.db(DbOp::PaperFill { order_id: o.order_id.clone(), slot, at_ms: now, sol_delta: proceeds as i64, token_delta: -(tokens.0 as i64), curve_fees: curve_fees.0, network_fee: fee, price_impact_bps: impact.0 });
                self.position_op(&o.mint, now);
                if out.closed {
                    self.finish_close(&o.mint, fees_paid, None);
                }
            }
            (PaperSide::Sell { exit_intent, .. }, FillResult::Failed { reason }) => {
                self.wallet = self.wallet.saturating_sub(fee);
                self.failed_tx_fees += fee;
                if let Some(h) = self.positions.get_mut(&o.mint) {
                    let _ = h.position.on_exit_failed(exit_intent, now);
                }
                self.risk.on_exec_failure(&cfg);
                self.inc("sell_failed");
                let k = format!("sell_failed: {}", reason_key(&reason));
                self.inc(&k);
                self.db(DbOp::Order { order_id: o.order_id.clone(), intent_id: o.intent_id.clone(), side: "sell".into(), mint: o.mint.clone(), position_id: Some(o.position_id.clone()), requested_lamports: None, requested_tokens: None, min_out: o.min_out(), status: "failed".into(), failure_reason: Some(reason.clone()) });
                self.log("sell_failed", &o.mint, serde_json::json!({"reason": reason}));
            }
            _ => {}
        }
    }

    fn finish_close(&mut self, mint: &str, fees_paid: u64, reason_override: Option<&str>) {
        let Some(h) = self.positions.remove(mint) else { return };
        let rent = self.cfg.paper.token_account_rent.0;
        self.wallet += rent;
        self.rent_locked = self.rent_locked.saturating_sub(rent);
        let reason = reason_override.map(str::to_owned).unwrap_or_else(|| h.position.exit_reason.as_ref().map(|r| format!("{r:?}")).unwrap_or_default());
        let ct = ClosedTrade { mint: mint.to_string(), opened_ms: h.position.opened_at.unwrap_or(0), closed_ms: self.chain_ms, cost: h.position.cost.0 as i64, proceeds: h.position.proceeds.0 as i64, fees: fees_paid as i64, exit_reason: reason.clone() };
        self.inc("position_closed");
        self.log("closed", mint, serde_json::json!({"pnl": ct.pnl(), "cost": ct.cost, "proceeds": ct.proceeds, "reason": reason, "hold_s": (ct.closed_ms - ct.opened_ms) / 1000}));
        self.closed.push(ct);
        self.emit_snapshot(self.chain_ms);
    }

    fn position_op(&mut self, mint: &str, now: i64) {
        let Some(h) = self.positions.get(mint) else { return };
        let p = &h.position;
        let op = DbOp::Position {
            position_id: p.id.clone(),
            mint: p.mint.clone(),
            state: serde_json::to_value(p.state).ok().and_then(|v| v.as_str().map(str::to_owned)).unwrap_or_default(),
            strategy: h.strategy.clone(),
            entry_intent_id: h.entry_intent.clone(),
            cost: p.cost.0,
            tokens_held: p.tokens_held.0,
            tokens_bought: p.tokens_bought.0,
            proceeds: p.proceeds.0,
            peak_value: p.peak_value.0,
            tp_levels_hit: p.tp_levels_hit as u16,
            opened_ms: p.opened_at,
            closed_ms: (p.state == pf_position::PositionState::Closed).then_some(now),
            exit_reason: p.exit_reason.as_ref().map(|r| format!("{r:?}")),
            state_json: serde_json::to_value(p).unwrap_or_default(),
        };
        self.db(op);
    }

    // ------------------------------------------------------------ exits

    fn evaluate_position(&mut self, mint: &str, now: i64, tip: u64) {
        let Some(t) = self.book.tokens.get(mint) else { return };
        let Some(h) = self.positions.get(mint) else { return };
        if h.position.state == pf_position::PositionState::EntryPending {
            return;
        }
        if t.complete {
            self.close_migrated(mint, now);
            return;
        }
        let value = t.liquidation_value(h.position.tokens_held);
        let (_, a) = self.facts_and_assessment(t, now);
        let tick = Tick {
            now,
            value,
            liquidity: Lamports(t.curve.real_sol_reserves),
            momentum: a.momentum,
            suspicious: t.creator_sold && !h.creator_sold_at_entry,
            portfolio_risk_exit: self.risk.breakers.contains_key(&Breaker::Drawdown) || self.risk.breakers.contains_key(&Breaker::DailyCombinedLoss),
        };
        let cfg = self.cfg.clone();
        let Some(h) = self.positions.get_mut(mint) else { return };
        h.last_value = value.0;
        if let Some(order) = h.position.evaluate(&cfg, &tick) {
            self.submit_sell(mint, order, now, tip);
        }
    }

    fn submit_sell(&mut self, mint: &str, order: ExitOrder, now: i64, tip: u64) {
        let Some(t) = self.book.tokens.get(mint) else { return };
        let quote = t.curve.quote_sell(order.tokens, t.fees);
        let max = self.cfg.risk.max_slippage;
        // A failed exit is retried as urgent: chasing a falling price with
        // normal tolerance burns fees (seen in tests). Urgent slippage is
        // still capped by the risk engine (2x, max 50%).
        let retry = self.positions.get(mint).is_some_and(|h| h.position.exit_failures > 0);
        let urgent = order.urgent || retry;
        let slip = if urgent { Bps((max.0 * 2).min(5_000)) } else { max };
        let req = SellRequest { intent_id: order.intent_id.clone(), mint: mint.to_string(), mode: TradingMode::Paper, max_slippage: slip, est_tx_fee: self.cfg.paper.network_fee, urgent };
        let cfg = self.cfg.clone();
        let d = self.risk.evaluate_sell(&cfg, &req, now);
        let position_id = self.positions.get(mint).map(|h| h.position.id.clone()).unwrap_or_default();
        let (Ok(q), true) = (quote, d.is_approved()) else {
            if let Some(h) = self.positions.get_mut(mint) {
                let _ = h.position.on_exit_failed(&order.intent_id, now);
            }
            self.inc("sell_not_submitted");
            return;
        };
        let po = PaperOrder {
            order_id: Uuid::new_v4().to_string(),
            intent_id: order.intent_id.clone(),
            mint: mint.to_string(),
            position_id: position_id.clone(),
            side: PaperSide::Sell { tokens: order.tokens, quoted_sol: q.sol_out.0, exit_intent: order.intent_id.clone(), urgent },
            max_slippage: slip,
            target_slot: tip + self.cfg.paper.landing_slots,
            deadline_ms: now + self.cfg.paper.fill_timeout_ms as i64,
        };
        let k = format!("exit: {:?}", order.reason).split(['{', ' ']).take(2).collect::<Vec<_>>().join(" ");
        self.inc(k.trim());
        self.db(DbOp::Order { order_id: po.order_id.clone(), intent_id: po.intent_id.clone(), side: "sell".into(), mint: mint.to_string(), position_id: Some(position_id), requested_lamports: None, requested_tokens: Some(order.tokens.0), min_out: po.min_out(), status: "submitted".into(), failure_reason: None });
        self.log("sell_submitted", mint, serde_json::json!({"tokens": order.tokens.0, "reason": format!("{:?}", order.reason), "urgent": urgent, "slippage_bps": slip.0}));
        self.pending.push(po);
    }

    /// The curve completed while we held. Selling then requires the AMM,
    /// which is not modelled: we close at the final curve price and label
    /// the exit so the report shows how often this happened.
    fn close_migrated(&mut self, mint: &str, now: i64) {
        let fee = self.cfg.paper.network_fee.0;
        let cfg = self.cfg.clone();
        // fail any in-flight orders for this mint
        let stale: Vec<PaperOrder> = self.pending.iter().filter(|o| o.mint == mint).cloned().collect();
        self.pending.retain(|o| o.mint != mint);
        for o in stale {
            if let PaperSide::Sell { exit_intent, .. } = &o.side {
                if let Some(h) = self.positions.get_mut(mint) {
                    let _ = h.position.on_exit_failed(exit_intent, now);
                }
            }
        }
        let Some(t) = self.book.tokens.get(mint) else { return };
        let Some(h) = self.positions.get_mut(mint) else { return };
        if h.position.state == pf_position::PositionState::EntryPending {
            let _ = h.position.on_entry_failed(now, "curve completed before fill");
            let intent = h.entry_intent.clone();
            self.positions.remove(mint);
            self.risk.on_buy_failed(&cfg, &intent);
            return;
        }
        let Some(order) = h.position.emergency_exit(now) else { return };
        let mut c = t.curve;
        c.complete = false;
        let Ok(q) = c.quote_sell(order.tokens, t.fees) else { return };
        let proceeds = q.sol_out.0.saturating_sub(fee);
        self.wallet += proceeds;
        let Ok(out) = h.position.on_exit_filled(&order.intent_id, order.tokens, Lamports(proceeds), now) else { return };
        h.fees_paid += q.fee.0 + fee;
        let fees_paid = h.fees_paid;
        self.risk.on_sell_filled(&cfg, &SellFill { mint: mint.to_string(), cost_released: out.cost_released, proceeds: Lamports(proceeds), fees: Lamports(q.fee.0 + fee), position_closed: out.closed, position_pnl: out.position_pnl });
        self.inc("exit: migrated (approximated)");
        self.position_op(mint, now);
        if out.closed {
            self.finish_close(mint, fees_paid, Some("MigratedApproximated"));
        }
    }

    // ------------------------------------------------------------ reporting

    pub fn report(&self) -> Report {
        let p = self.portfolio();
        let open_cost: u64 = self.positions.values().map(|h| h.position.cost_remaining.0).sum();
        let mut by_reason = BTreeMap::new();
        for c in &self.closed {
            *by_reason.entry(c.exit_reason.split(['{', ' ']).next().unwrap_or("").to_string()).or_insert(0) += 1;
        }
        // wallet = start + realised pnl − failed tx fees − open cost basis + open partial proceeds − rent locked
        let realised: i64 = self.closed.iter().map(|c| c.pnl()).sum();
        let open_net: i64 = self.positions.values().filter(|h| h.position.state != pf_position::PositionState::EntryPending).map(|h| h.position.proceeds.0 as i64 - h.position.cost.0 as i64).sum();
        let expected = self.starting_balance as i64 + realised - self.failed_tx_fees as i64 + open_net - self.rent_locked as i64;
        Report {
            mode: "paper",
            starting_balance_sol: sol(self.starting_balance),
            wallet_sol: sol(self.wallet),
            equity_sol: sol(self.wallet + p.open_value.0 + self.rent_locked),
            open_positions: self.positions.len(),
            open_cost_sol: sol(open_cost),
            open_value_sol: sol(p.open_value.0),
            rent_locked_sol: sol(self.rent_locked),
            failed_tx_fees_sol: sol(self.failed_tx_fees),
            closed: summarize(&self.closed),
            breakers: self.risk.breakers.iter().map(|(b, r)| format!("{b:?}: {r}")).collect(),
            counters: self.counters.clone(),
            exits_by_reason: by_reason,
            accounting_check_lamports: self.wallet as i64 - expected,
        }
    }

    pub fn closed_trades(&self) -> &[ClosedTrade] {
        &self.closed
    }

    pub fn open_mints(&self) -> Vec<String> {
        self.positions.keys().cloned().collect()
    }
}

/// Liquidation value even for a just-completed curve (final curve price).
fn final_value(t: &TokenState, tokens: TokenAmount) -> Lamports {
    let mut c = t.curve;
    c.complete = false;
    c.liquidation_value(tokens, t.fees)
}

#[cfg(test)]
mod tests {
    use super::reason_key;

    #[test]
    fn reason_keys_group_numbers_away() {
        assert_eq!(reason_key("age 16s < 20s"), "age");
        assert_eq!(reason_key("5 trades < 15"), "trades");
        assert_eq!(reason_key("top-10 holders 31.00% > 30.00%"), "holders");
        assert_eq!(reason_key("breaker Drawdown: dd"), "breaker Drawdown");
        assert!(reason_key("quote asset pumpCmXqMfrsAkQ5r49WcJnRayYRqmXz6ae8 is not SOL").contains("pumpCmXq"));
    }
}
