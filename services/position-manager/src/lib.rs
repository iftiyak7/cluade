//! Position lifecycle and adaptive exit logic.
//!
//! Exit classes:
//!
//! * **Mandatory** (always active, cannot be disabled by any switch):
//!   hard stop-loss, suspicious activity, liquidity collapse, portfolio-risk
//!   exit, emergency/manual close, maximum holding time.
//! * **Discretionary** (respect `auto_sell`, `take_profit`, `trailing_stop`,
//!   `momentum_analysis` switches): TP ladder, trailing stop, momentum loss.
//!
//! Gains are measured on a *price* basis: the current liquidation value of
//! the remaining tokens is scaled back up to the original token count, so
//! thresholds stay meaningful after partial exits. Liquidation value already
//! includes curve price impact and fees, so it is conservative.
//!
//! Smart Hold only engages on a *profitable* position with strong momentum,
//! and only defers the TP ladder; trailing stop and all mandatory exits stay
//! live. A losing position is never extended.

use pf_config::TradingConfig;
use pf_types::{change_bps, Bps, Lamports, TimestampMs, TokenAmount};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum PositionState {
    EntryPending,
    Open,
    Holding,
    PartialExit,
    ExitPending,
    Closed,
    RecoveryRequired,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ExitReason {
    TakeProfit { level: usize },
    TrailingStop,
    HardStopLoss,
    MomentumLoss,
    LiquidityDrop,
    SuspiciousActivity,
    MaxHoldTime,
    PortfolioRisk,
    Emergency,
}

impl ExitReason {
    pub fn is_urgent(&self) -> bool {
        matches!(self, ExitReason::HardStopLoss | ExitReason::LiquidityDrop | ExitReason::SuspiciousActivity | ExitReason::PortfolioRisk | ExitReason::Emergency)
    }
}

/// Market observation for one position at one instant.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Tick {
    pub now: TimestampMs,
    /// Liquidation value of the position's *currently held* tokens.
    pub value: Lamports,
    /// Real SOL in the bonding curve / pool.
    pub liquidity: Lamports,
    pub momentum: u8,
    pub suspicious: bool,
    /// Set by risk-engine when a portfolio-wide limit requires de-risking.
    pub portfolio_risk_exit: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ExitOrder {
    pub intent_id: String,
    pub tokens: TokenAmount,
    pub reason: ExitReason,
    pub urgent: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
struct PendingExit {
    intent_id: String,
    tokens: TokenAmount,
    reason: ExitReason,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Transition {
    pub at: TimestampMs,
    pub from: PositionState,
    pub to: PositionState,
    pub reason: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Position {
    pub id: String,
    pub mint: String,
    pub state: PositionState,
    pub cost: Lamports,
    pub cost_remaining: Lamports,
    pub tokens_bought: TokenAmount,
    pub tokens_held: TokenAmount,
    pub proceeds: Lamports,
    pub opened_at: Option<TimestampMs>,
    pub entry_liquidity: Lamports,
    /// Peak of price-normalised full-size value.
    pub peak_value: Lamports,
    pub trailing_armed: bool,
    pub tp_levels_hit: usize,
    pub exit_failures: u32,
    pub exit_reason: Option<ExitReason>,
    pending: Vec<PendingExit>,
    seq: u64,
    pub history: Vec<Transition>,
}

/// Returned on each sell fill so the caller can update the risk engine.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SellFillOutcome {
    pub cost_released: Lamports,
    pub proceeds: Lamports,
    pub closed: bool,
    /// Total realised PnL of the position (only meaningful when closed).
    pub position_pnl: i64,
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum PositionError {
    #[error("invalid transition: {0:?} on {1}")]
    InvalidState(PositionState, &'static str),
    #[error("unknown exit intent {0}")]
    UnknownIntent(String),
}

fn mul_div(a: u64, b: u64, c: u64) -> u64 {
    if c == 0 {
        return 0;
    }
    (a as u128 * b as u128 / c as u128) as u64
}

impl Position {
    pub fn new(id: impl Into<String>, mint: impl Into<String>, now: TimestampMs) -> Self {
        let mut p = Self {
            id: id.into(),
            mint: mint.into(),
            state: PositionState::EntryPending,
            cost: Lamports::ZERO,
            cost_remaining: Lamports::ZERO,
            tokens_bought: TokenAmount(0),
            tokens_held: TokenAmount(0),
            proceeds: Lamports::ZERO,
            opened_at: None,
            entry_liquidity: Lamports::ZERO,
            peak_value: Lamports::ZERO,
            trailing_armed: false,
            tp_levels_hit: 0,
            exit_failures: 0,
            exit_reason: None,
            pending: Vec::new(),
            seq: 0,
            history: Vec::new(),
        };
        p.history.push(Transition { at: now, from: PositionState::EntryPending, to: PositionState::EntryPending, reason: "created".into() });
        p
    }

    fn go(&mut self, to: PositionState, at: TimestampMs, reason: impl Into<String>) {
        if self.state != to {
            self.history.push(Transition { at, from: self.state, to, reason: reason.into() });
            self.state = to;
        }
    }

    pub fn is_active(&self) -> bool {
        !matches!(self.state, PositionState::EntryPending | PositionState::Closed)
    }

    /// Entry confirmed on-chain. `cost` = actual SOL spent incl. fees.
    pub fn on_entry_filled(&mut self, cost: Lamports, tokens: TokenAmount, liquidity: Lamports, now: TimestampMs) -> Result<(), PositionError> {
        if self.state != PositionState::EntryPending {
            return Err(PositionError::InvalidState(self.state, "entry_filled"));
        }
        self.cost = cost;
        self.cost_remaining = cost;
        self.tokens_bought = tokens;
        self.tokens_held = tokens;
        self.opened_at = Some(now);
        self.entry_liquidity = liquidity;
        self.peak_value = cost;
        self.go(PositionState::Open, now, "entry filled");
        Ok(())
    }

    pub fn on_entry_failed(&mut self, now: TimestampMs, why: &str) -> Result<(), PositionError> {
        if self.state != PositionState::EntryPending {
            return Err(PositionError::InvalidState(self.state, "entry_failed"));
        }
        self.go(PositionState::Closed, now, format!("entry failed: {why}"));
        Ok(())
    }

    /// Intent ids of exits awaiting a fill/failure (needed for restart
    /// recovery, where in-flight orders must be reconciled or failed).
    pub fn pending_exit_intents(&self) -> Vec<String> {
        self.pending.iter().map(|p| p.intent_id.clone()).collect()
    }

    fn pending_tokens(&self) -> u64 {
        self.pending.iter().map(|p| p.tokens.0).sum()
    }

    fn available(&self) -> TokenAmount {
        TokenAmount(self.tokens_held.0.saturating_sub(self.pending_tokens()))
    }

    /// Current value scaled to the original position size.
    fn full_size_value(&self, held_value: Lamports) -> Lamports {
        Lamports(mul_div(held_value.0, self.tokens_bought.0, self.tokens_held.0))
    }

    /// Gain vs cost basis in bps (price basis).
    pub fn gain_bps(&self, held_value: Lamports) -> i64 {
        change_bps(self.cost, self.full_size_value(held_value))
    }

    fn next_intent(&mut self) -> String {
        self.seq += 1;
        format!("{}:exit:{}", self.id, self.seq)
    }

    fn issue(&mut self, tokens: TokenAmount, reason: ExitReason, now: TimestampMs) -> Option<ExitOrder> {
        if tokens.0 == 0 {
            return None;
        }
        let intent_id = self.next_intent();
        let urgent = reason.is_urgent();
        let full = tokens.0 + self.pending_tokens() >= self.tokens_held.0;
        self.pending.push(PendingExit { intent_id: intent_id.clone(), tokens, reason: reason.clone() });
        if full {
            self.go(PositionState::ExitPending, now, format!("exit: {reason:?}"));
        }
        Some(ExitOrder { intent_id, tokens, reason, urgent })
    }

    /// Operator emergency close. Ignores switches.
    pub fn emergency_exit(&mut self, now: TimestampMs) -> Option<ExitOrder> {
        if !self.is_active() || self.state == PositionState::RecoveryRequired {
            return None;
        }
        if self.pending.iter().any(|p| p.reason.is_urgent()) {
            return None;
        }
        let avail = self.available();
        self.issue(avail, ExitReason::Emergency, now)
    }

    /// Evaluate one market tick. Returns at most one new exit order.
    pub fn evaluate(&mut self, cfg: &TradingConfig, t: &Tick) -> Option<ExitOrder> {
        if !self.is_active() || self.state == PositionState::RecoveryRequired || self.tokens_held.0 == 0 {
            return None;
        }
        let x = &cfg.exits;
        let sw = &cfg.switches;
        let gain = self.gain_bps(t.value);
        let fsv = self.full_size_value(t.value);
        if fsv > self.peak_value {
            self.peak_value = fsv;
        }
        if sw.trailing_stop && gain >= x.trailing_activation_gain.0 as i64 {
            self.trailing_armed = true;
        }

        // ---- mandatory exits (sell everything not already pending)
        // Suspicion exits even with anti_rug off: anti_rug gates *entry* filtering only.
        let mandatory = if t.suspicious {
            Some(ExitReason::SuspiciousActivity)
        } else if gain <= -(x.hard_stop_loss.0 as i64) {
            Some(ExitReason::HardStopLoss)
        } else if self.entry_liquidity.0 > 0 && -change_bps(self.entry_liquidity, t.liquidity) >= x.liquidity_drop_exit.0 as i64 {
            Some(ExitReason::LiquidityDrop)
        } else if t.portfolio_risk_exit {
            Some(ExitReason::PortfolioRisk)
        } else if self.opened_at.is_some_and(|o| t.now - o >= cfg.risk.max_hold_secs as i64 * 1000) {
            Some(ExitReason::MaxHoldTime)
        } else {
            None
        };
        if let Some(reason) = mandatory {
            // Urgent exits may be added on top of a pending non-urgent sell.
            let already = self.pending.iter().any(|p| p.reason.is_urgent() || p.reason == reason);
            if already {
                return None;
            }
            let avail = self.available();
            return self.issue(avail, reason, t.now);
        }

        if !self.pending.is_empty() || !sw.auto_sell {
            return None;
        }

        // ---- smart hold state
        let strong = sw.smart_hold && sw.momentum_analysis && gain > 0 && t.momentum >= x.smart_hold_min_momentum;
        if strong && matches!(self.state, PositionState::Open | PositionState::PartialExit) {
            self.go(PositionState::Holding, t.now, format!("smart hold: momentum {}", t.momentum));
        } else if !strong && self.state == PositionState::Holding {
            let back = if self.tp_levels_hit > 0 { PositionState::PartialExit } else { PositionState::Open };
            self.go(back, t.now, format!("smart hold released: momentum {}", t.momentum));
        }
        let weakening = sw.smart_hold && t.momentum < x.smart_hold_min_momentum;

        // ---- trailing stop
        if self.trailing_armed {
            let dist = if weakening { x.trailing_distance_weak } else { x.trailing_distance };
            let off_peak = -change_bps(self.peak_value, fsv);
            if off_peak >= dist.0 as i64 {
                let avail = self.available();
                return self.issue(avail, ExitReason::TrailingStop, t.now);
            }
        }

        // ---- momentum loss
        if sw.momentum_analysis && t.momentum < x.momentum_exit_below {
            let avail = self.available();
            return self.issue(avail, ExitReason::MomentumLoss, t.now);
        }

        // ---- take-profit ladder (deferred while holding)
        if sw.take_profit && self.state != PositionState::Holding {
            if let Some(lvl) = x.take_profit_levels.get(self.tp_levels_hit) {
                if gain >= lvl.trigger_gain.0 as i64 {
                    let mut qty = self.tokens_bought.mul_bps(lvl.sell_fraction);
                    qty = TokenAmount(qty.0.min(self.available().0));
                    // Without a trailing stop, the last level closes the remainder.
                    let last = self.tp_levels_hit + 1 == x.take_profit_levels.len();
                    if last && !sw.trailing_stop {
                        qty = self.available();
                    }
                    let level = self.tp_levels_hit;
                    return self.issue(qty, ExitReason::TakeProfit { level }, t.now);
                }
            }
        }
        None
    }

    pub fn on_exit_filled(&mut self, intent_id: &str, tokens_sold: TokenAmount, proceeds: Lamports, now: TimestampMs) -> Result<SellFillOutcome, PositionError> {
        let idx = self.pending.iter().position(|p| p.intent_id == intent_id).ok_or_else(|| PositionError::UnknownIntent(intent_id.into()))?;
        let p = self.pending.remove(idx);
        let sold = TokenAmount(tokens_sold.0.min(self.tokens_held.0));
        let cost_released = Lamports(mul_div(self.cost_remaining.0, sold.0, self.tokens_held.0));
        self.tokens_held = self.tokens_held.saturating_sub(sold);
        self.cost_remaining = self.cost_remaining.saturating_sub(cost_released);
        self.proceeds = self.proceeds.saturating_add(proceeds);
        self.exit_failures = 0;
        if let ExitReason::TakeProfit { level } = p.reason {
            self.tp_levels_hit = self.tp_levels_hit.max(level + 1);
        }
        let closed = self.tokens_held.0 == 0;
        if closed {
            self.pending.clear();
            self.exit_reason = Some(p.reason.clone());
            self.go(PositionState::Closed, now, format!("closed: {:?}", p.reason));
        } else if self.pending.is_empty() {
            self.go(PositionState::PartialExit, now, format!("partial: {:?}", p.reason));
        }
        Ok(SellFillOutcome { cost_released, proceeds, closed, position_pnl: self.realized_pnl() })
    }

    /// Sell failed / expired. The position becomes eligible for re-evaluation
    /// on the next tick (mandatory exits will be re-issued).
    pub fn on_exit_failed(&mut self, intent_id: &str, now: TimestampMs) -> Result<(), PositionError> {
        let idx = self.pending.iter().position(|p| p.intent_id == intent_id).ok_or_else(|| PositionError::UnknownIntent(intent_id.into()))?;
        let p = self.pending.remove(idx);
        self.exit_failures += 1;
        if self.state == PositionState::ExitPending && self.pending_tokens() < self.tokens_held.0 {
            let back = if self.tp_levels_hit > 0 { PositionState::PartialExit } else { PositionState::Open };
            self.go(back, now, format!("exit failed: {:?}", p.reason));
        }
        Ok(())
    }

    /// Compare on-chain balance with our books. Any mismatch beyond
    /// `tolerance` base units requires operator attention.
    pub fn reconcile(&mut self, observed: TokenAmount, tolerance: u64, now: TimestampMs) -> bool {
        if !self.is_active() {
            return true;
        }
        if self.tokens_held.0.abs_diff(observed.0) > tolerance {
            self.go(PositionState::RecoveryRequired, now, format!("balance mismatch: books {} chain {}", self.tokens_held.0, observed.0));
            return false;
        }
        true
    }

    pub fn realized_pnl(&self) -> i64 {
        let sold_cost = self.cost.saturating_sub(self.cost_remaining);
        self.proceeds.0 as i64 - sold_cost.0 as i64
    }

    pub fn unrealized_pnl(&self, held_value: Lamports) -> i64 {
        held_value.0 as i64 - self.cost_remaining.0 as i64
    }
}

/// Convenience: percent → Bps for tests/config UIs.
pub fn pct(p: u32) -> Bps {
    Bps::percent(p)
}

#[cfg(test)]
mod tests {
    use super::*;

    const COST: u64 = 100_000_000; // 0.1 SOL
    const TOK: u64 = 1_000_000_000;

    fn cfg() -> TradingConfig {
        let mut c = TradingConfig::default();
        c.switches.master = true;
        c.switches.auto_buy = true;
        c
    }

    fn open(now: i64) -> Position {
        let mut p = Position::new("p1", "M", now);
        p.on_entry_filled(Lamports(COST), TokenAmount(TOK), Lamports(10_000_000_000), now).unwrap();
        p
    }

    /// Tick where the *held* tokens are worth `pct_of_cost`% of cost at full size.
    fn tick(p: &Position, now: i64, pct_of_cost: u64, momentum: u8) -> Tick {
        let full = COST * pct_of_cost / 100;
        Tick { now, value: Lamports(mul_div(full, p.tokens_held.0, p.tokens_bought.0)), liquidity: Lamports(10_000_000_000), momentum, suspicious: false, portfolio_risk_exit: false }
    }

    fn fill(p: &mut Position, o: &ExitOrder, now: i64, pct_of_cost: u64) -> SellFillOutcome {
        let proceeds = mul_div(COST * pct_of_cost / 100, o.tokens.0, TOK);
        p.on_exit_filled(&o.intent_id, o.tokens, Lamports(proceeds), now).unwrap()
    }

    #[test]
    fn lifecycle_entry() {
        let mut p = Position::new("p", "M", 0);
        assert_eq!(p.state, PositionState::EntryPending);
        assert!(p.evaluate(&cfg(), &Tick { now: 1, value: Lamports(1), liquidity: Lamports(1), momentum: 50, suspicious: true, portfolio_risk_exit: false }).is_none(), "no exits before fill");
        p.on_entry_failed(1, "expired").unwrap();
        assert_eq!(p.state, PositionState::Closed);
        assert!(p.on_entry_filled(Lamports(1), TokenAmount(1), Lamports(1), 2).is_err());
    }

    #[test]
    fn hard_stop_loss_full_exit_and_urgent() {
        let c = cfg();
        let mut p = open(0);
        assert!(p.evaluate(&c, &tick(&p, 1, 85, 50)).is_none());
        let o = p.evaluate(&c, &tick(&p, 2, 79, 50)).unwrap();
        assert_eq!(o.reason, ExitReason::HardStopLoss);
        assert!(o.urgent);
        assert_eq!(o.tokens, TokenAmount(TOK));
        assert_eq!(p.state, PositionState::ExitPending);
        assert!(p.evaluate(&c, &tick(&p, 3, 70, 50)).is_none(), "no duplicate exit while pending");
        let out = fill(&mut p, &o, 4, 79);
        assert!(out.closed);
        assert_eq!(p.state, PositionState::Closed);
        assert_eq!(out.position_pnl, -(21_000_000));
    }

    #[test]
    fn hard_stop_cannot_be_disabled_by_switches() {
        let mut c = cfg();
        c.switches.auto_sell = false;
        c.switches.take_profit = false;
        c.switches.trailing_stop = false;
        c.switches.momentum_analysis = false;
        let mut p = open(0);
        assert_eq!(p.evaluate(&c, &tick(&p, 1, 50, 50)).unwrap().reason, ExitReason::HardStopLoss);
    }

    #[test]
    fn tp_ladder_then_trailing_stop_on_remainder() {
        let c = cfg();
        let mut p = open(0);
        // TP1 at +20%: sell 25%
        let o1 = p.evaluate(&c, &tick(&p, 1, 121, 50)).unwrap();
        assert_eq!(o1.reason, ExitReason::TakeProfit { level: 0 });
        assert_eq!(o1.tokens, TokenAmount(TOK / 4));
        fill(&mut p, &o1, 2, 121);
        assert_eq!(p.state, PositionState::PartialExit);
        // TP2 at +40%
        let o2 = p.evaluate(&c, &tick(&p, 3, 141, 50)).unwrap();
        assert_eq!(o2.reason, ExitReason::TakeProfit { level: 1 });
        fill(&mut p, &o2, 4, 141);
        // TP3 at +70%
        let o3 = p.evaluate(&c, &tick(&p, 5, 171, 50)).unwrap();
        assert_eq!(o3.reason, ExitReason::TakeProfit { level: 2 });
        fill(&mut p, &o3, 6, 171);
        assert_eq!(p.tokens_held, TokenAmount(TOK / 4));
        // rally to +150%, then pull back 15% from peak -> trailing stop
        assert!(p.evaluate(&c, &tick(&p, 7, 250, 50)).is_none());
        assert!(p.evaluate(&c, &tick(&p, 8, 215, 50)).is_none(), "14% off peak: hold");
        let o4 = p.evaluate(&c, &tick(&p, 9, 212, 50)).unwrap();
        assert_eq!(o4.reason, ExitReason::TrailingStop);
        let out = fill(&mut p, &o4, 10, 212);
        assert!(out.closed);
        // pnl = 0.25*(1.21+1.41+1.71+2.12)*COST - COST
        assert_eq!(p.realized_pnl(), 61_250_000);
    }

    #[test]
    fn trailing_stop_example_from_spec() {
        // entry 10 -> 12 arms -> 15 -> reversal sells
        let mut c = cfg();
        c.switches.take_profit = false;
        let mut p = open(0);
        assert!(p.evaluate(&c, &tick(&p, 1, 120, 50)).is_none());
        assert!(p.trailing_armed);
        assert!(p.evaluate(&c, &tick(&p, 2, 150, 50)).is_none());
        assert_eq!(p.evaluate(&c, &tick(&p, 3, 127, 50)).unwrap().reason, ExitReason::TrailingStop);
    }

    #[test]
    fn smart_hold_defers_tp_but_not_trailing() {
        let mut c = cfg();
        c.switches.smart_hold = true;
        let mut p = open(0);
        assert!(p.evaluate(&c, &tick(&p, 1, 130, 80)).is_none(), "strong momentum: no TP1");
        assert_eq!(p.state, PositionState::Holding);
        assert!(p.evaluate(&c, &tick(&p, 2, 200, 85)).is_none());
        // momentum weakens -> leaves Holding, tighter trailing (8%) fires at 184 (8% off 200)
        let o = p.evaluate(&c, &tick(&p, 3, 184, 50)).unwrap();
        assert_eq!(o.reason, ExitReason::TrailingStop);
    }

    #[test]
    fn smart_hold_never_extends_losers() {
        let mut c = cfg();
        c.switches.smart_hold = true;
        let mut p = open(0);
        assert!(p.evaluate(&c, &tick(&p, 1, 90, 95)).is_none());
        assert_eq!(p.state, PositionState::Open, "no hold on a losing position");
        assert_eq!(p.evaluate(&c, &tick(&p, 2, 79, 99)).unwrap().reason, ExitReason::HardStopLoss);
    }

    #[test]
    fn max_hold_time_applies_even_in_smart_hold() {
        let mut c = cfg();
        c.switches.smart_hold = true;
        let mut p = open(0);
        assert!(p.evaluate(&c, &tick(&p, 1_000, 130, 90)).is_none());
        let limit = c.risk.max_hold_secs as i64 * 1000;
        assert_eq!(p.evaluate(&c, &tick(&p, limit, 130, 90)).unwrap().reason, ExitReason::MaxHoldTime);
    }

    #[test]
    fn momentum_liquidity_suspicious_exits() {
        let c = cfg();
        let mut p = open(0);
        assert_eq!(p.evaluate(&c, &tick(&p, 1, 100, 10)).unwrap().reason, ExitReason::MomentumLoss);

        let mut p = open(0);
        let mut t = tick(&p, 1, 105, 50);
        t.liquidity = Lamports(5_000_000_000);
        assert_eq!(p.evaluate(&c, &t).unwrap().reason, ExitReason::LiquidityDrop);

        let mut p = open(0);
        let mut t = tick(&p, 1, 110, 50);
        t.suspicious = true;
        assert_eq!(p.evaluate(&c, &t).unwrap().reason, ExitReason::SuspiciousActivity);
    }

    #[test]
    fn urgent_exit_stacks_on_pending_partial() {
        let c = cfg();
        let mut p = open(0);
        let tp = p.evaluate(&c, &tick(&p, 1, 121, 50)).unwrap();
        assert_eq!(tp.tokens, TokenAmount(TOK / 4));
        let sl = p.evaluate(&c, &tick(&p, 2, 70, 50)).unwrap();
        assert_eq!(sl.reason, ExitReason::HardStopLoss);
        assert_eq!(sl.tokens, TokenAmount(TOK - TOK / 4), "only sells what is not already pending");
        assert_eq!(p.state, PositionState::ExitPending);
        p.on_exit_failed(&tp.intent_id, 3).unwrap();
        fill(&mut p, &sl, 4, 70);
        assert_eq!(p.tokens_held, TokenAmount(TOK / 4));
        // remaining quarter gets a new stop-loss order next tick
        assert_eq!(p.evaluate(&c, &tick(&p, 5, 70, 50)).unwrap().tokens, TokenAmount(TOK / 4));
    }

    #[test]
    fn failed_exit_is_retried() {
        let c = cfg();
        let mut p = open(0);
        let o = p.evaluate(&c, &tick(&p, 1, 70, 50)).unwrap();
        p.on_exit_failed(&o.intent_id, 2).unwrap();
        assert_eq!(p.state, PositionState::Open);
        let o2 = p.evaluate(&c, &tick(&p, 3, 70, 50)).unwrap();
        assert_ne!(o.intent_id, o2.intent_id, "new intent id per attempt");
        assert_eq!(p.exit_failures, 1);
    }

    #[test]
    fn reconciliation_mismatch_requires_recovery() {
        let c = cfg();
        let mut p = open(0);
        assert!(p.reconcile(TokenAmount(TOK), 0, 1));
        assert!(!p.reconcile(TokenAmount(TOK / 2), 0, 2));
        assert_eq!(p.state, PositionState::RecoveryRequired);
        assert!(p.evaluate(&c, &tick(&p, 3, 10, 0)).is_none(), "operator must resolve");
    }

    #[test]
    fn emergency_exit_and_persistence() {
        let mut p = open(0);
        let json = serde_json::to_string(&p).unwrap();
        let mut restored: Position = serde_json::from_str(&json).unwrap();
        assert_eq!(restored, p);
        let o = restored.emergency_exit(1).unwrap();
        assert_eq!(o.tokens, TokenAmount(TOK));
        assert!(restored.emergency_exit(2).is_none());
        p.on_entry_failed(3, "x").unwrap_err();
    }
}
