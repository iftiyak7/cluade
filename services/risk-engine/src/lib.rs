//! Risk engine — final authority on every order.
//!
//! Properties this crate guarantees (each covered by a test below):
//!
//! * A buy is approved only if every limit holds *after* the buy, counting
//!   both filled exposure and in-flight reservations.
//! * The same intent id can never be approved twice (idempotency).
//! * Loss / drawdown / failure circuit breakers block new **buys** only.
//!   Exits are never blocked by loss limits — blocking a sell during a
//!   losing streak would turn a risk control into a risk.
//! * State is fully serialisable; persisting and reloading it (restart)
//!   preserves daily counters, reservations and tripped breakers.
//! * Live orders require both live gates in the config.
//!
//! The engine is deterministic: time is passed in (`now`), no I/O.

use pf_config::{ProfitTargetAction, TradingConfig};
use pf_types::{change_bps, Address, Bps, Lamports, RiskClass, TimestampMs, TradingMode, BPS_DENOMINATOR};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};

const DAY_MS: i64 = 86_400_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Breaker {
    DailyRealizedLoss,
    DailyCombinedLoss,
    Drawdown,
    ConsecutiveLosses,
    ExecutionFailures,
    /// Set by the health monitor when RPC/Geyser data is unreliable.
    DataUnreliable,
    /// Set on wallet/position reconciliation mismatch.
    Reconciliation,
    DailyProfitTarget,
}

impl Breaker {
    /// Breakers that clear automatically at the UTC day boundary.
    fn resets_daily(self) -> bool {
        matches!(self, Breaker::DailyRealizedLoss | Breaker::DailyCombinedLoss | Breaker::DailyProfitTarget)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
pub struct OperatorFlags {
    pub stop_new_buys: bool,
    pub pause_all_orders: bool,
    pub safe_mode: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
struct Reservation {
    mint: Address,
    amount: Lamports,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct RiskState {
    pub mode: TradingMode,
    pub day: i64,
    pub realized_pnl_today: i64,
    pub fees_today: Lamports,
    pub buys_today: u32,
    pub consecutive_losses: u32,
    pub consecutive_exec_failures: u32,
    pub equity_peak: Lamports,
    /// Filled cost basis currently at risk, per mint.
    exposure: BTreeMap<Address, Lamports>,
    /// Approved but not yet filled/failed.
    reservations: BTreeMap<String, Reservation>,
    /// Every intent id ever decided (bounded by daily pruning in persistence layer).
    decided: BTreeSet<String>,
    pub breakers: BTreeMap<Breaker, String>,
    pub flags: OperatorFlags,
}

/// Facts about the wallet/portfolio at decision time.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Portfolio {
    pub wallet_balance: Lamports,
    /// Current liquidation value of all open positions.
    pub open_value: Lamports,
    /// Unrealised PnL across open positions (signed).
    pub unrealized_pnl: i64,
}

impl Portfolio {
    pub fn equity(&self) -> Lamports {
        self.wallet_balance.saturating_add(self.open_value)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BuyRequest {
    pub intent_id: String,
    pub mint: Address,
    pub mode: TradingMode,
    pub size: Lamports,
    pub risk_class: RiskClass,
    pub max_slippage: Bps,
    pub price_impact: Bps,
    pub est_tx_fee: Lamports,
    pub market_data_age_ms: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SellRequest {
    pub intent_id: String,
    pub mint: Address,
    pub mode: TradingMode,
    pub max_slippage: Bps,
    pub est_tx_fee: Lamports,
    /// Stop-loss / emergency exits get a wider slippage allowance.
    pub urgent: bool,
}

/// A confirmed sell, as reported to the risk engine.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SellFill {
    pub mint: Address,
    /// Share of the position's cost basis that this sell released.
    pub cost_released: Lamports,
    /// SOL received, net of all fees.
    pub proceeds: Lamports,
    pub fees: Lamports,
    pub position_closed: bool,
    /// Total realised PnL of the position (used when closed).
    pub position_pnl: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Decision {
    Approved { size: Lamports, notes: Vec<String> },
    Denied { reasons: Vec<String> },
}

impl Decision {
    pub fn is_approved(&self) -> bool {
        matches!(self, Decision::Approved { .. })
    }
    pub fn approved_size(&self) -> Option<Lamports> {
        match self {
            Decision::Approved { size, .. } => Some(*size),
            _ => None,
        }
    }
}

impl RiskState {
    pub fn new(mode: TradingMode, now: TimestampMs, starting_equity: Lamports) -> Self {
        Self {
            mode,
            day: now.div_euclid(DAY_MS),
            realized_pnl_today: 0,
            fees_today: Lamports::ZERO,
            buys_today: 0,
            consecutive_losses: 0,
            consecutive_exec_failures: 0,
            equity_peak: starting_equity,
            exposure: BTreeMap::new(),
            reservations: BTreeMap::new(),
            decided: BTreeSet::new(),
            breakers: BTreeMap::new(),
            flags: OperatorFlags::default(),
        }
    }

    pub fn total_exposure(&self) -> Lamports {
        let filled = self.exposure.values().fold(Lamports::ZERO, |a, b| a.saturating_add(*b));
        self.reservations.values().fold(filled, |a, r| a.saturating_add(r.amount))
    }

    pub fn token_exposure(&self, mint: &str) -> Lamports {
        let filled = self.exposure.get(mint).copied().unwrap_or_default();
        self.reservations.values().filter(|r| r.mint == mint).fold(filled, |a, r| a.saturating_add(r.amount))
    }

    pub fn open_position_count(&self) -> usize {
        let mut mints: BTreeSet<&str> = self.exposure.keys().map(|s| s.as_str()).collect();
        mints.extend(self.reservations.values().map(|r| r.mint.as_str()));
        mints.len()
    }

    fn roll_day(&mut self, now: TimestampMs) {
        let day = now.div_euclid(DAY_MS);
        if day != self.day {
            self.day = day;
            self.realized_pnl_today = 0;
            self.fees_today = Lamports::ZERO;
            self.buys_today = 0;
            self.breakers.retain(|b, _| !b.resets_daily());
        }
    }

    pub fn trip(&mut self, b: Breaker, reason: impl Into<String>) {
        self.breakers.entry(b).or_insert_with(|| reason.into());
    }

    /// Manual reset by an administrator (audited by admin-api).
    pub fn reset_breaker(&mut self, b: Breaker) {
        self.breakers.remove(&b);
        match b {
            Breaker::ConsecutiveLosses => self.consecutive_losses = 0,
            Breaker::ExecutionFailures => self.consecutive_exec_failures = 0,
            _ => {}
        }
    }

    /// Re-evaluate portfolio-level breakers. Called on every snapshot tick
    /// and before every buy decision.
    pub fn observe_portfolio(&mut self, cfg: &TradingConfig, p: &Portfolio, now: TimestampMs) {
        self.roll_day(now);
        let r = &cfg.risk;
        let equity = p.equity();
        if equity > self.equity_peak {
            self.equity_peak = equity;
        }
        let dd = -change_bps(self.equity_peak, equity);
        if dd >= r.max_drawdown.0 as i64 {
            self.trip(Breaker::Drawdown, format!("drawdown {dd} bps from peak {}", self.equity_peak));
        }
        let combined = self.realized_pnl_today + p.unrealized_pnl;
        if combined <= -(r.max_daily_combined_loss.0 as i64) {
            self.trip(Breaker::DailyCombinedLoss, format!("combined daily pnl {combined} lamports"));
        }
    }

    pub fn evaluate_buy(&mut self, cfg: &TradingConfig, req: &BuyRequest, p: &Portfolio, now: TimestampMs) -> Decision {
        self.observe_portfolio(cfg, p, now);
        let r = &cfg.risk;
        let mut deny = Vec::new();

        if !self.decided.insert(req.intent_id.clone()) {
            // Do not touch reservations: the first decision stands.
            return Decision::Denied { reasons: vec![format!("duplicate intent {}", req.intent_id)] };
        }

        // --- gates
        if req.mode != self.mode {
            deny.push(format!("intent mode {:?} != engine mode {:?}", req.mode, self.mode));
        }
        if req.mode == TradingMode::Live && !cfg.live_orders_allowed() {
            deny.push("live trading not enabled".into());
        }
        if !cfg.switches.master {
            deny.push("master switch off".into());
        }
        if !cfg.switches.auto_buy {
            deny.push("auto-buy off".into());
        }
        if self.flags.stop_new_buys || self.flags.pause_all_orders || self.flags.safe_mode {
            deny.push(format!("operator flags {:?}", self.flags));
        }
        for (b, why) in &self.breakers {
            deny.push(format!("breaker {b:?}: {why}"));
        }

        // --- per-request checks
        if self.realized_pnl_today <= -(r.max_daily_realized_loss.0 as i64) {
            deny.push("daily realized loss limit reached".into());
        }
        if self.buys_today >= r.max_daily_buys {
            deny.push(format!("daily buy count {} reached", r.max_daily_buys));
        }
        if req.market_data_age_ms > r.max_market_data_age_ms {
            deny.push(format!("market data stale ({} ms)", req.market_data_age_ms));
        }
        if req.max_slippage > r.max_slippage {
            deny.push(format!("slippage {} bps > max {}", req.max_slippage.0, r.max_slippage.0));
        }
        if req.price_impact > r.max_price_impact {
            deny.push(format!("price impact {} bps > max {}", req.price_impact.0, r.max_price_impact.0));
        }
        if req.est_tx_fee > r.max_tx_fee {
            deny.push(format!("tx fee {} > max {}", req.est_tx_fee, r.max_tx_fee));
        }
        let already_in_token = self.token_exposure(&req.mint).0 > 0;
        if !already_in_token && self.open_position_count() >= r.max_open_positions as usize {
            deny.push(format!("max open positions {} reached", r.max_open_positions));
        }

        // --- sizing (clamp down, never up)
        let mut size = req.size;
        let mut notes = Vec::new();
        let class_mult = match req.risk_class {
            RiskClass::Low => cfg.sizing.low_risk,
            RiskClass::Medium => cfg.sizing.medium_risk,
            RiskClass::High => cfg.sizing.high_risk,
        };
        if cfg.switches.dynamic_sizing {
            let class_cap = cfg.sizing.base_size.mul_bps(class_mult);
            if class_cap.0 == 0 {
                deny.push(format!("{:?} risk tokens not permitted", req.risk_class));
            }
            size = size.min(class_cap);
        }
        if let (Some(target), true) = (r.daily_profit_target, self.realized_pnl_today >= 0) {
            if self.realized_pnl_today as u64 >= target.0 {
                match r.on_profit_target {
                    ProfitTargetAction::PauseNewBuys => {
                        self.trip(Breaker::DailyProfitTarget, "daily profit target reached");
                        deny.push("daily profit target reached: new buys paused".into());
                    }
                    ProfitTargetAction::ReduceSize(b) => {
                        size = size.mul_bps(b);
                        notes.push(format!("profit target reached: size scaled to {} bps", b.0));
                    }
                    ProfitTargetAction::Continue => {}
                }
            }
        }
        let spendable = p
            .wallet_balance
            .saturating_sub(r.min_wallet_reserve)
            .saturating_sub(req.est_tx_fee);
        let caps = [
            ("max_per_trade", r.max_per_trade),
            ("max_per_token", r.max_per_token.saturating_sub(self.token_exposure(&req.mint))),
            ("max_total_exposure", r.max_total_exposure.saturating_sub(self.total_exposure())),
            ("wallet reserve", spendable),
        ];
        for (name, cap) in caps {
            if size > cap {
                notes.push(format!("clamped by {name} to {cap}"));
                size = cap;
            }
        }
        if size < r.min_order {
            deny.push(format!("size {size} below min order {} after limits", r.min_order));
        }

        if !deny.is_empty() {
            return Decision::Denied { reasons: deny };
        }
        self.reservations.insert(req.intent_id.clone(), Reservation { mint: req.mint.clone(), amount: size });
        self.buys_today += 1;
        Decision::Approved { size, notes }
    }

    pub fn evaluate_sell(&mut self, cfg: &TradingConfig, req: &SellRequest, now: TimestampMs) -> Decision {
        self.roll_day(now);
        let r = &cfg.risk;
        let mut deny = Vec::new();
        if !self.decided.insert(req.intent_id.clone()) {
            return Decision::Denied { reasons: vec![format!("duplicate intent {}", req.intent_id)] };
        }
        if req.mode != self.mode {
            deny.push("mode mismatch".into());
        }
        if req.mode == TradingMode::Live && !cfg.live_orders_allowed() {
            deny.push("live trading not enabled".into());
        }
        if self.flags.pause_all_orders {
            deny.push("all orders paused by operator".into());
        }
        // Urgent exits may use up to 2x the slippage allowance (capped at 50%).
        let slip_cap = if req.urgent { Bps((r.max_slippage.0 * 2).min(5_000)) } else { r.max_slippage };
        if req.max_slippage > slip_cap {
            deny.push(format!("slippage {} bps > cap {}", req.max_slippage.0, slip_cap.0));
        }
        if req.est_tx_fee > r.max_tx_fee {
            deny.push(format!("tx fee {} > max {}", req.est_tx_fee, r.max_tx_fee));
        }
        // Breakers and loss limits intentionally NOT checked here.
        if deny.is_empty() {
            Decision::Approved { size: Lamports::ZERO, notes: vec![] }
        } else {
            Decision::Denied { reasons: deny }
        }
    }

    /// Buy confirmed on-chain with the *actual* SOL spent (incl. fees).
    pub fn on_buy_filled(&mut self, intent_id: &str, actual_cost: Lamports, fees: Lamports) {
        if let Some(res) = self.reservations.remove(intent_id) {
            let e = self.exposure.entry(res.mint).or_default();
            *e = e.saturating_add(actual_cost);
        }
        self.fees_today = self.fees_today.saturating_add(fees);
        self.consecutive_exec_failures = 0;
    }

    /// Buy failed/expired: release the reservation. The buy still counts
    /// toward `buys_today` (attempts, not fills) to bound retry storms.
    pub fn on_buy_failed(&mut self, cfg: &TradingConfig, intent_id: &str) {
        self.reservations.remove(intent_id);
        self.on_exec_failure(cfg);
    }

    pub fn on_exec_failure(&mut self, cfg: &TradingConfig) {
        self.consecutive_exec_failures += 1;
        if self.consecutive_exec_failures >= cfg.risk.max_consecutive_exec_failures {
            self.trip(Breaker::ExecutionFailures, format!("{} consecutive execution failures", self.consecutive_exec_failures));
        }
    }

    /// Partial or full sell filled (observed on-chain).
    pub fn on_sell_filled(&mut self, cfg: &TradingConfig, f: &SellFill) {
        let SellFill { ref mint, cost_released, proceeds, fees, position_closed, position_pnl } = *f;
        let mint = mint.as_str();
        if let Some(e) = self.exposure.get_mut(mint) {
            *e = e.saturating_sub(cost_released);
            if position_closed || e.0 == 0 {
                self.exposure.remove(mint);
            }
        }
        self.realized_pnl_today += proceeds.0 as i64 - cost_released.0 as i64;
        self.fees_today = self.fees_today.saturating_add(fees);
        self.consecutive_exec_failures = 0;

        let r = &cfg.risk;
        if position_closed {
            if position_pnl < 0 {
                self.consecutive_losses += 1;
            } else {
                self.consecutive_losses = 0;
            }
        }
        if self.consecutive_losses >= r.max_consecutive_losses {
            self.trip(Breaker::ConsecutiveLosses, format!("{} consecutive losing trades", self.consecutive_losses));
        }
        if self.realized_pnl_today <= -(r.max_daily_realized_loss.0 as i64) {
            self.trip(Breaker::DailyRealizedLoss, format!("realized pnl today {} lamports", self.realized_pnl_today));
        }
    }

    pub fn new_buys_blocked(&self) -> bool {
        !self.breakers.is_empty() || self.flags.stop_new_buys || self.flags.pause_all_orders || self.flags.safe_mode
    }
}

/// Helper for callers: `a * b / 10_000` on u64.
pub fn bps_of(a: u64, b: Bps) -> u64 {
    (a as u128 * b.0 as u128 / BPS_DENOMINATOR as u128) as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    const SOL: u64 = 1_000_000_000;

    fn cfg() -> TradingConfig {
        let mut c = TradingConfig::default();
        c.switches.master = true;
        c.switches.auto_buy = true;
        c
    }
    fn wallet(sol_milli: u64) -> Portfolio {
        Portfolio { wallet_balance: Lamports::from_sol_milli(sol_milli), open_value: Lamports::ZERO, unrealized_pnl: 0 }
    }
    fn buy(id: &str, mint: &str, milli: u64) -> BuyRequest {
        BuyRequest {
            intent_id: id.into(),
            mint: mint.into(),
            mode: TradingMode::Paper,
            size: Lamports::from_sol_milli(milli),
            risk_class: RiskClass::Low,
            max_slippage: Bps::percent(5),
            price_impact: Bps::percent(1),
            est_tx_fee: Lamports(10_000),
            market_data_age_ms: 100,
        }
    }
    fn sell(id: &str, urgent: bool) -> SellRequest {
        SellRequest { intent_id: id.into(), mint: "M1".into(), mode: TradingMode::Paper, max_slippage: Bps::percent(15), est_tx_fee: Lamports(10_000), urgent }
    }
    fn state() -> RiskState {
        RiskState::new(TradingMode::Paper, 0, Lamports(SOL))
    }

    #[test]
    fn approves_within_limits() {
        let mut s = state();
        let d = s.evaluate_buy(&cfg(), &buy("a", "M1", 50), &wallet(1000), 0);
        assert_eq!(d.approved_size(), Some(Lamports::from_sol_milli(50)));
        assert_eq!(s.total_exposure(), Lamports::from_sol_milli(50));
    }

    #[test]
    fn defaults_deny_everything_until_switched_on() {
        let mut s = state();
        let d = s.evaluate_buy(&TradingConfig::default(), &buy("a", "M1", 50), &wallet(1000), 0);
        assert!(!d.is_approved());
    }

    #[test]
    fn duplicate_intent_is_rejected_and_does_not_double_reserve() {
        let mut s = state();
        assert!(s.evaluate_buy(&cfg(), &buy("a", "M1", 50), &wallet(1000), 0).is_approved());
        assert!(!s.evaluate_buy(&cfg(), &buy("a", "M1", 50), &wallet(1000), 0).is_approved());
        assert_eq!(s.total_exposure(), Lamports::from_sol_milli(50));
    }

    #[test]
    fn clamps_to_per_trade_and_exposure() {
        let mut c = cfg();
        c.switches.dynamic_sizing = false;
        let mut s = state();
        let d = s.evaluate_buy(&c, &buy("a", "M1", 500), &wallet(1000), 0);
        assert_eq!(d.approved_size(), Some(c.risk.max_per_trade));
        // fill up total exposure (250 mSOL) across mints
        for (i, m) in ["M2", "M3", "M4", "M5"].iter().enumerate() {
            assert!(s.evaluate_buy(&c, &buy(&format!("b{i}"), m, 50), &wallet(1000), 0).is_approved());
        }
        assert_eq!(s.total_exposure(), c.risk.max_total_exposure);
        // max_open_positions (5) reached AND exposure full
        let d = s.evaluate_buy(&c, &buy("z", "M6", 50), &wallet(1000), 0);
        assert!(!d.is_approved(), "{d:?}");
    }

    #[test]
    fn wallet_reserve_is_never_spent() {
        // start equity == wallet so the drawdown breaker is not what denies
        let mut s = RiskState::new(TradingMode::Paper, 0, Lamports::from_sol_milli(60));
        // 60 mSOL wallet, 50 mSOL reserve -> ~10 mSOL spendable
        let d = s.evaluate_buy(&cfg(), &buy("a", "M1", 50), &wallet(60), 0);
        let size = d.approved_size().unwrap();
        assert!(size.0 <= Lamports::from_sol_milli(10).0 - 10_000, "{size}");
        // 50 mSOL wallet -> nothing spendable
        let d = s.evaluate_buy(&cfg(), &buy("b", "M2", 50), &wallet(50), 0);
        assert!(!d.is_approved());
    }

    #[test]
    fn dynamic_sizing_by_risk_class() {
        let c = cfg();
        let mut s = state();
        let mut r = buy("m", "M1", 50);
        r.risk_class = RiskClass::Medium;
        assert_eq!(s.evaluate_buy(&c, &r, &wallet(1000), 0).approved_size(), Some(Lamports::from_sol_milli(25)));
        let mut r = buy("h", "M2", 50);
        r.risk_class = RiskClass::High;
        assert!(!s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
    }

    #[test]
    fn live_requires_both_gates() {
        let mut c = cfg();
        let mut s = RiskState::new(TradingMode::Live, 0, Lamports(SOL));
        let mut r = buy("a", "M1", 50);
        r.mode = TradingMode::Live;
        c.mode = TradingMode::Live;
        assert!(!s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
        c.live_trading_enabled = true;
        r.intent_id = "b".into();
        assert!(s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
    }

    #[test]
    fn daily_loss_trips_breaker_blocks_buys_but_not_sells() {
        let c = cfg();
        let mut s = state();
        assert!(s.evaluate_buy(&c, &buy("a", "M1", 50), &wallet(1000), 0).is_approved());
        s.on_buy_filled("a", Lamports::from_sol_milli(50), Lamports(5_000));
        // lose 160 mSOL realised (limit 150) — simulate with an oversized loss
        s.on_sell_filled(&c, &SellFill { mint: "M1".into(), cost_released: Lamports::from_sol_milli(200), proceeds: Lamports::from_sol_milli(40), fees: Lamports(5_000), position_closed: true, position_pnl: -160_000_000 });
        assert!(s.breakers.contains_key(&Breaker::DailyRealizedLoss));
        assert!(!s.evaluate_buy(&c, &buy("b", "M2", 10), &wallet(1000), 1).is_approved());
        assert!(s.evaluate_sell(&c, &sell("s1", true), 1).is_approved(), "exits must not be blocked by loss limits");
    }

    #[test]
    fn daily_breakers_reset_next_day_but_drawdown_does_not() {
        let c = cfg();
        let mut s = state();
        s.trip(Breaker::DailyRealizedLoss, "x");
        s.trip(Breaker::Drawdown, "y");
        s.realized_pnl_today = -1;
        let d = s.evaluate_buy(&c, &buy("a", "M1", 10), &wallet(1000), DAY_MS + 5);
        assert!(!d.is_approved());
        assert!(!s.breakers.contains_key(&Breaker::DailyRealizedLoss));
        assert!(s.breakers.contains_key(&Breaker::Drawdown));
        assert_eq!(s.realized_pnl_today, 0);
        s.reset_breaker(Breaker::Drawdown);
        assert!(s.evaluate_buy(&c, &buy("b", "M1", 10), &wallet(1000), DAY_MS + 6).is_approved());
    }

    #[test]
    fn drawdown_breaker() {
        let c = cfg();
        let mut s = RiskState::new(TradingMode::Paper, 0, Lamports(SOL));
        s.observe_portfolio(&c, &Portfolio { wallet_balance: Lamports(2 * SOL), open_value: Lamports::ZERO, unrealized_pnl: 0 }, 0);
        assert_eq!(s.equity_peak, Lamports(2 * SOL));
        s.observe_portfolio(&c, &Portfolio { wallet_balance: Lamports(SOL + SOL / 2), open_value: Lamports::ZERO, unrealized_pnl: 0 }, 0);
        assert!(s.breakers.contains_key(&Breaker::Drawdown), "25% dd should trip");
    }

    #[test]
    fn combined_unrealized_loss_breaker() {
        let c = cfg();
        let mut s = state();
        s.observe_portfolio(&c, &Portfolio { wallet_balance: Lamports(SOL), open_value: Lamports(SOL), unrealized_pnl: -(Lamports::from_sol_milli(201).0 as i64) }, 0);
        assert!(s.breakers.contains_key(&Breaker::DailyCombinedLoss));
    }

    #[test]
    fn consecutive_losses_and_exec_failures() {
        let c = cfg();
        let mut s = state();
        for _ in 0..5 {
            s.on_sell_filled(&c, &SellFill { mint: "M".into(), cost_released: Lamports(10), proceeds: Lamports(9), fees: Lamports(0), position_closed: true, position_pnl: -1 });
        }
        assert!(s.breakers.contains_key(&Breaker::ConsecutiveLosses));
        let mut s = state();
        for i in 0..5 {
            assert!(s.evaluate_buy(&c, &buy(&format!("f{i}"), "M1", 10), &wallet(1000), 0).is_approved() || i > 0);
            s.on_buy_failed(&c, &format!("f{i}"));
        }
        assert!(s.breakers.contains_key(&Breaker::ExecutionFailures));
        assert_eq!(s.total_exposure(), Lamports::ZERO, "failed buys release reservations");
    }

    #[test]
    fn stale_data_slippage_impact_fee_denied() {
        let c = cfg();
        let mut s = state();
        let mut r = buy("a", "M1", 10);
        r.market_data_age_ms = 10_000;
        assert!(!s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
        let mut r = buy("b", "M1", 10);
        r.max_slippage = Bps::percent(30);
        assert!(!s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
        let mut r = buy("c", "M1", 10);
        r.price_impact = Bps::percent(9);
        assert!(!s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
        let mut r = buy("d", "M1", 10);
        r.est_tx_fee = Lamports(SOL);
        assert!(!s.evaluate_buy(&c, &r, &wallet(1000), 0).is_approved());
    }

    #[test]
    fn urgent_sells_get_wider_slippage() {
        let c = cfg();
        let mut s = state();
        assert!(!s.evaluate_sell(&c, &sell("a", false), 0).is_approved());
        assert!(s.evaluate_sell(&c, &sell("b", true), 0).is_approved());
        s.flags.pause_all_orders = true;
        assert!(!s.evaluate_sell(&c, &sell("c", true), 0).is_approved());
    }

    #[test]
    fn profit_target_actions() {
        let mut c = cfg();
        c.risk.daily_profit_target = Some(Lamports::from_sol_milli(100));
        let mut s = state();
        s.realized_pnl_today = Lamports::from_sol_milli(120).0 as i64;
        assert!(!s.evaluate_buy(&c, &buy("a", "M1", 50), &wallet(1000), 0).is_approved());
        c.risk.on_profit_target = ProfitTargetAction::ReduceSize(Bps::percent(50));
        let mut s = state();
        s.realized_pnl_today = Lamports::from_sol_milli(120).0 as i64;
        assert_eq!(s.evaluate_buy(&c, &buy("b", "M1", 50), &wallet(1000), 0).approved_size(), Some(Lamports::from_sol_milli(25)));
    }

    #[test]
    fn state_survives_restart() {
        let c = cfg();
        let mut s = state();
        assert!(s.evaluate_buy(&c, &buy("a", "M1", 50), &wallet(1000), 0).is_approved());
        s.trip(Breaker::Reconciliation, "mismatch");
        let persisted = serde_json::to_string(&s).unwrap();
        let mut restored: RiskState = serde_json::from_str(&persisted).unwrap();
        assert_eq!(restored, s);
        assert!(!restored.evaluate_buy(&c, &buy("a", "M1", 50), &wallet(1000), 0).is_approved(), "no duplicate after restart");
        assert!(!restored.evaluate_buy(&c, &buy("n", "M2", 50), &wallet(1000), 0).is_approved(), "breaker survives restart");
        assert_eq!(restored.buys_today, 1);
    }
}
