//! Typed runtime configuration.
//!
//! The admin API stores versions of this struct as JSON (`config_versions`);
//! every service deserialises it and MUST call [`TradingConfig::validate`]
//! before applying it. Invalid configurations are rejected wholesale — the
//! previous valid version stays active.
//!
//! Defaults are intentionally conservative: paper mode, live trading off.

use pf_types::{Bps, Lamports, TradingMode};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct TradingConfig {
    pub mode: TradingMode,
    /// Second, independent gate. Live orders require `mode == Live` AND this.
    pub live_trading_enabled: bool,
    pub switches: Switches,
    pub risk: RiskLimits,
    pub sizing: Sizing,
    pub exits: ExitConfig,
    pub scoring: ScoringConfig,
    pub entry: EntryConfig,
    pub paper: PaperConfig,
}

impl Default for TradingConfig {
    fn default() -> Self {
        Self {
            mode: TradingMode::Paper,
            live_trading_enabled: false,
            switches: Switches::default(),
            risk: RiskLimits::default(),
            sizing: Sizing::default(),
            exits: ExitConfig::default(),
            scoring: ScoringConfig::default(),
            entry: EntryConfig::default(),
            paper: PaperConfig::default(),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum BuyMode {
    /// Act on the first eligible evaluation (fastest; most exposed to rugs).
    InstantSniper,
    /// Wait for `min_age_secs` and `min_trades` of real activity.
    SmartEntry,
    /// SmartEntry plus stricter scores (`conservative_*`) and half size.
    Conservative,
    /// SmartEntry plus a positive short-term trend and buyer acceleration.
    MomentumConfirmation,
    /// Produce intents but never execute without operator approval.
    Manual,
}

/// Discovery / entry filters (Building 03 + 05 admin settings).
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct EntryConfig {
    pub buy_mode: BuyMode,
    pub new_launches: bool,
    pub min_age_secs: u64,
    pub max_age_secs: u64,
    pub min_trades: u32,
    pub min_unique_buyers: u32,
    /// Real quote reserves in the curve.
    pub min_liquidity: Lamports,
    pub min_volume_1m: Lamports,
    pub max_market_cap: Lamports,
    /// Only SOL-quoted curves can be traded with a SOL wallet.
    pub require_sol_quote: bool,
    /// Mayhem-mode curves have different dynamics; excluded unless enabled.
    pub allow_mayhem: bool,
    pub conservative_min_safety: u8,
    pub momentum_min_price_change_1m_bps: i64,
    pub token_blacklist: Vec<String>,
    pub creator_blacklist: Vec<String>,
    /// Re-evaluate a token at most this often.
    pub reevaluate_every_ms: u64,
}

impl Default for EntryConfig {
    fn default() -> Self {
        Self {
            buy_mode: BuyMode::SmartEntry,
            new_launches: true,
            min_age_secs: 20,
            max_age_secs: 15 * 60,
            min_trades: 15,
            min_unique_buyers: 8,
            min_liquidity: Lamports::from_sol_milli(1_500),
            min_volume_1m: Lamports::from_sol_milli(500),
            max_market_cap: Lamports(400 * 1_000_000_000),
            require_sol_quote: true,
            allow_mayhem: false,
            conservative_min_safety: 85,
            momentum_min_price_change_1m_bps: 500,
            token_blacklist: Vec::new(),
            creator_blacklist: Vec::new(),
            reevaluate_every_ms: 2_000,
        }
    }
}

/// How paper fills are modelled. Defaults are deliberately pessimistic.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct PaperConfig {
    pub starting_balance: Lamports,
    /// Slots between our decision (at the current chain tip) and landing.
    pub landing_slots: u64,
    /// If no trade arrives to price the fill, fill at last state after this.
    pub fill_timeout_ms: u64,
    /// Base + priority fee per transaction (also charged on failed txs).
    pub network_fee: Lamports,
    /// Rent locked in the token account; refunded when the position closes.
    pub token_account_rent: Lamports,
}

impl Default for PaperConfig {
    fn default() -> Self {
        Self {
            starting_balance: Lamports::from_sol_milli(1_000),
            landing_slots: 2,
            fill_timeout_ms: 5_000,
            network_fee: Lamports(105_000),
            token_account_rent: Lamports(2_074_080),
        }
    }
}

/// Operator ON/OFF switches. Note there is deliberately NO switch that
/// disables the hard risk limits in [`RiskLimits`].
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Switches {
    pub master: bool,
    pub scanner: bool,
    pub auto_buy: bool,
    pub auto_sell: bool,
    pub smart_hold: bool,
    pub take_profit: bool,
    pub trailing_stop: bool,
    pub momentum_analysis: bool,
    pub creator_analysis: bool,
    pub anti_rug: bool,
    pub dynamic_sizing: bool,
    pub telegram_alerts: bool,
    pub rpc_failover: bool,
    pub auto_recovery: bool,
}

impl Default for Switches {
    fn default() -> Self {
        Self {
            master: false,
            scanner: true,
            auto_buy: false,
            auto_sell: true,
            smart_hold: false,
            take_profit: true,
            trailing_stop: true,
            momentum_analysis: true,
            creator_analysis: true,
            anti_rug: true,
            dynamic_sizing: true,
            telegram_alerts: true,
            rpc_failover: true,
            auto_recovery: true,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct RiskLimits {
    pub max_per_trade: Lamports,
    pub max_per_token: Lamports,
    pub max_total_exposure: Lamports,
    pub max_open_positions: u32,
    pub max_daily_buys: u32,
    /// Max realised loss per UTC day (positive number).
    pub max_daily_realized_loss: Lamports,
    /// Max realised + unrealised loss per UTC day.
    pub max_daily_combined_loss: Lamports,
    /// Max drawdown from equity peak, in bps of the peak.
    pub max_drawdown: Bps,
    pub max_consecutive_losses: u32,
    pub max_slippage: Bps,
    pub max_price_impact: Bps,
    /// Max network + priority fee per transaction.
    pub max_tx_fee: Lamports,
    /// Wallet balance that may never be spent (covers fees for exits).
    pub min_wallet_reserve: Lamports,
    pub max_hold_secs: u64,
    pub max_consecutive_exec_failures: u32,
    pub max_market_data_age_ms: u64,
    /// Optional daily profit target — an operating rule, not an expectation.
    pub daily_profit_target: Option<Lamports>,
    pub on_profit_target: ProfitTargetAction,
    /// Smallest order the risk engine will approve after clamping.
    pub min_order: Lamports,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case", tag = "action", content = "size")]
pub enum ProfitTargetAction {
    PauseNewBuys,
    /// Continue with sizes scaled to this fraction.
    ReduceSize(Bps),
    Continue,
}

impl Default for RiskLimits {
    fn default() -> Self {
        Self {
            max_per_trade: Lamports::from_sol_milli(50),
            max_per_token: Lamports::from_sol_milli(50),
            max_total_exposure: Lamports::from_sol_milli(250),
            max_open_positions: 5,
            max_daily_buys: 20,
            max_daily_realized_loss: Lamports::from_sol_milli(150),
            max_daily_combined_loss: Lamports::from_sol_milli(200),
            max_drawdown: Bps::percent(25),
            max_consecutive_losses: 5,
            max_slippage: Bps::percent(10),
            max_price_impact: Bps::percent(5),
            max_tx_fee: Lamports(2_000_000), // 0.002 SOL
            min_wallet_reserve: Lamports::from_sol_milli(50),
            max_hold_secs: 60 * 60,
            max_consecutive_exec_failures: 5,
            max_market_data_age_ms: 3_000,
            daily_profit_target: None,
            on_profit_target: ProfitTargetAction::PauseNewBuys,
            min_order: Lamports(1_000_000), // 0.001 SOL
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Sizing {
    pub base_size: Lamports,
    /// Multiplier (bps of base_size) per risk class when dynamic sizing is on.
    pub low_risk: Bps,
    pub medium_risk: Bps,
    /// 0 = never buy high-risk tokens.
    pub high_risk: Bps,
}

impl Default for Sizing {
    fn default() -> Self {
        Self {
            base_size: Lamports::from_sol_milli(50),
            low_risk: Bps::percent(100),
            medium_risk: Bps::percent(50),
            high_risk: Bps(0),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TakeProfitLevel {
    /// Gain over cost basis that triggers this level.
    pub trigger_gain: Bps,
    /// Fraction of the ORIGINAL position size to sell at this level.
    pub sell_fraction: Bps,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct ExitConfig {
    /// Hard stop: loss vs cost basis that forces a full exit. Always active.
    pub hard_stop_loss: Bps,
    pub take_profit_levels: Vec<TakeProfitLevel>,
    /// Trailing stop arms once gain reaches this.
    pub trailing_activation_gain: Bps,
    /// Exit when value falls this far below the peak value.
    pub trailing_distance: Bps,
    /// Tighter trailing distance while smart-hold momentum is weakening.
    pub trailing_distance_weak: Bps,
    /// Momentum score below which smart-hold is lost / momentum exit fires.
    pub momentum_exit_below: u8,
    /// Momentum score at or above which smart-hold engages.
    pub smart_hold_min_momentum: u8,
    /// Liquidity drop (vs entry) that triggers an exit.
    pub liquidity_drop_exit: Bps,
}

impl Default for ExitConfig {
    fn default() -> Self {
        Self {
            hard_stop_loss: Bps::percent(20),
            take_profit_levels: vec![
                TakeProfitLevel { trigger_gain: Bps::percent(20), sell_fraction: Bps::percent(25) },
                TakeProfitLevel { trigger_gain: Bps::percent(40), sell_fraction: Bps::percent(25) },
                TakeProfitLevel { trigger_gain: Bps::percent(70), sell_fraction: Bps::percent(25) },
            ],
            trailing_activation_gain: Bps::percent(20),
            trailing_distance: Bps::percent(15),
            trailing_distance_weak: Bps::percent(8),
            momentum_exit_below: 25,
            smart_hold_min_momentum: 65,
            liquidity_drop_exit: Bps::percent(40),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct ScoringConfig {
    pub min_safety: u8,
    pub min_momentum: u8,
    pub min_liquidity: u8,
    pub min_opportunity: u8,
    /// Weights for the opportunity score; must sum to 100.
    pub weight_safety: u8,
    pub weight_momentum: u8,
    pub weight_liquidity: u8,
    /// Max share of supply held by top-10 holders (excl. bonding curve).
    pub max_top10_concentration: Bps,
    pub max_creator_holding: Bps,
    pub max_creator_prior_rugs: u32,
}

impl Default for ScoringConfig {
    fn default() -> Self {
        Self {
            min_safety: 70,
            min_momentum: 50,
            min_liquidity: 40,
            min_opportunity: 60,
            weight_safety: 50,
            weight_momentum: 30,
            weight_liquidity: 20,
            max_top10_concentration: Bps::percent(30),
            max_creator_holding: Bps::percent(10),
            max_creator_prior_rugs: 0,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum ConfigError {
    #[error("{0}")]
    Invalid(String),
}

fn check(cond: bool, msg: &str, errs: &mut Vec<String>) {
    if !cond {
        errs.push(msg.to_string());
    }
}

impl TradingConfig {
    /// Server-side validation. Returns every problem, not just the first.
    pub fn validate(&self) -> Result<(), Vec<ConfigError>> {
        let mut e = Vec::new();
        let r = &self.risk;
        let x = &self.exits;
        let s = &self.scoring;

        check(
            !(self.live_trading_enabled && self.mode == TradingMode::Paper),
            "live_trading_enabled requires mode=live",
            &mut e,
        );
        check(r.max_per_trade.0 > 0, "risk.max_per_trade must be > 0", &mut e);
        check(r.max_per_trade <= r.max_per_token, "max_per_trade must be <= max_per_token", &mut e);
        check(r.max_per_token <= r.max_total_exposure, "max_per_token must be <= max_total_exposure", &mut e);
        check(r.max_open_positions > 0, "max_open_positions must be > 0", &mut e);
        check(r.max_daily_realized_loss.0 > 0, "max_daily_realized_loss must be > 0", &mut e);
        check(
            r.max_daily_combined_loss >= r.max_daily_realized_loss,
            "max_daily_combined_loss must be >= max_daily_realized_loss",
            &mut e,
        );
        check(r.max_drawdown.0 > 0 && r.max_drawdown.0 <= 10_000, "max_drawdown must be in (0,100%]", &mut e);
        check(r.max_slippage.0 <= 5_000, "max_slippage above 50% is refused", &mut e);
        check(r.max_hold_secs > 0, "max_hold_secs must be > 0", &mut e);
        check(r.max_consecutive_losses > 0, "max_consecutive_losses must be > 0", &mut e);
        check(r.min_order <= r.max_per_trade, "min_order must be <= max_per_trade", &mut e);
        if let ProfitTargetAction::ReduceSize(b) = r.on_profit_target {
            check(b.0 > 0 && b.0 <= 10_000, "on_profit_target reduce size must be in (0,100%]", &mut e);
        }
        check(r.max_market_data_age_ms > 0, "max_market_data_age_ms must be > 0", &mut e);

        check(self.sizing.base_size.0 > 0, "sizing.base_size must be > 0", &mut e);
        for (name, b) in [("low", self.sizing.low_risk), ("medium", self.sizing.medium_risk), ("high", self.sizing.high_risk)] {
            check(b.0 <= 10_000, &format!("sizing.{name}_risk must be <= 100%"), &mut e);
        }

        check(x.hard_stop_loss.0 > 0 && x.hard_stop_loss.0 < 10_000, "hard_stop_loss must be in (0,100%)", &mut e);
        check(x.trailing_distance.0 > 0 && x.trailing_distance.0 < 10_000, "trailing_distance must be in (0,100%)", &mut e);
        check(x.trailing_distance_weak <= x.trailing_distance, "trailing_distance_weak must be <= trailing_distance", &mut e);
        let mut prev = 0u32;
        let mut total_fraction = 0u32;
        for (i, lvl) in x.take_profit_levels.iter().enumerate() {
            check(lvl.trigger_gain.0 > prev, &format!("take_profit_levels[{i}] triggers must strictly increase"), &mut e);
            check(lvl.sell_fraction.0 > 0, &format!("take_profit_levels[{i}].sell_fraction must be > 0"), &mut e);
            prev = lvl.trigger_gain.0;
            total_fraction += lvl.sell_fraction.0;
        }
        check(total_fraction <= 10_000, "take-profit sell fractions sum to more than 100%", &mut e);
        check(x.momentum_exit_below < x.smart_hold_min_momentum, "momentum_exit_below must be < smart_hold_min_momentum", &mut e);

        check(
            s.weight_safety as u32 + s.weight_momentum as u32 + s.weight_liquidity as u32 == 100,
            "scoring weights must sum to 100",
            &mut e,
        );
        for (n, v) in [("min_safety", s.min_safety), ("min_momentum", s.min_momentum), ("min_liquidity", s.min_liquidity), ("min_opportunity", s.min_opportunity)] {
            check(v <= 100, &format!("scoring.{n} must be <= 100"), &mut e);
        }

        let en = &self.entry;
        check(en.min_age_secs < en.max_age_secs, "entry.min_age_secs must be < max_age_secs", &mut e);
        check(en.reevaluate_every_ms > 0, "entry.reevaluate_every_ms must be > 0", &mut e);
        check(en.conservative_min_safety <= 100, "entry.conservative_min_safety must be <= 100", &mut e);
        check(self.paper.starting_balance.0 > 0, "paper.starting_balance must be > 0", &mut e);
        check(self.paper.fill_timeout_ms > 0, "paper.fill_timeout_ms must be > 0", &mut e);

        if e.is_empty() {
            Ok(())
        } else {
            Err(e.into_iter().map(ConfigError::Invalid).collect())
        }
    }

    /// True only when BOTH live gates are open.
    pub fn live_orders_allowed(&self) -> bool {
        self.mode == TradingMode::Live && self.live_trading_enabled
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_is_valid_and_paper() {
        let c = TradingConfig::default();
        c.validate().unwrap();
        assert_eq!(c.mode, TradingMode::Paper);
        assert!(!c.live_orders_allowed());
        assert!(!c.switches.master, "master switch must default OFF");
        assert!(!c.switches.auto_buy, "auto-buy must default OFF");
    }

    #[test]
    fn live_flag_without_live_mode_rejected() {
        let c = TradingConfig { live_trading_enabled: true, ..Default::default() };
        assert!(c.validate().is_err());
    }

    #[test]
    fn rejects_inconsistent_limits() {
        let mut c = TradingConfig::default();
        c.risk.max_per_trade = Lamports::from_sol_milli(500);
        c.scoring.weight_safety = 90;
        c.exits.take_profit_levels.push(TakeProfitLevel { trigger_gain: Bps::percent(10), sell_fraction: Bps::percent(50) });
        let errs = c.validate().unwrap_err();
        assert!(errs.len() >= 3, "{errs:?}");
    }

    #[test]
    fn json_roundtrip_with_partial_input() {
        // Missing fields fall back to defaults — the admin API can send partial docs.
        let c: TradingConfig = serde_json::from_str(r#"{"risk":{"max_open_positions":3}}"#).unwrap();
        assert_eq!(c.risk.max_open_positions, 3);
        assert_eq!(c.risk.max_per_trade, RiskLimits::default().max_per_trade);
        c.validate().unwrap();
        let s = serde_json::to_string(&c).unwrap();
        assert_eq!(serde_json::from_str::<TradingConfig>(&s).unwrap(), c);
    }
}
