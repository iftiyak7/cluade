//! Token scoring: Safety, Momentum, Liquidity Quality (0–100 each) plus a
//! weighted Opportunity score, with every contributing factor recorded.
//!
//! A high score is a *filter*, not a prediction: it means "fewer known red
//! flags and more observed activity", never "safe" or "profitable".
//!
//! Hard rejects short-circuit eligibility regardless of score. Blacklists
//! and on-chain authority checks are hard rejects even with `anti_rug` off,
//! because no configuration should allow buying a freezable token.

use pf_config::TradingConfig;
use pf_types::{Bps, Lamports, RiskClass};
use serde::{Deserialize, Serialize};

/// Static / slow-changing facts about the token and creator.
#[derive(Debug, Clone, PartialEq, Eq, Default, Serialize, Deserialize)]
pub struct TokenFacts {
    pub mint_authority_present: bool,
    pub freeze_authority_present: bool,
    /// Token-2022 extensions that can block or tax transfers
    /// (transfer hook, permanent delegate, transfer fee, non-transferable,
    /// default-frozen account state).
    pub dangerous_extensions: Vec<String>,
    pub mint_blacklisted: bool,
    pub creator_blacklisted: bool,
    pub creator_prior_launches: u32,
    pub creator_prior_rugs: u32,
    pub top10_concentration: Bps,
    pub creator_holding: Bps,
    /// Supply share held by wallets funded from a common source.
    pub cluster_share: Bps,
    /// Supply share bought in the creation slot by non-creator wallets (bundling).
    pub same_slot_buy_share: Bps,
    /// A non-creator wallet has successfully sold (empirical sellability).
    pub sell_observed: bool,
    /// Creator has sold since launch.
    pub creator_sold: bool,
}

/// Rolling market activity.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize)]
pub struct MarketStats {
    pub age_secs: u64,
    pub unique_buyers_1m: u32,
    pub unique_buyers_5m: u32,
    pub buy_volume_1m: Lamports,
    pub sell_volume_1m: Lamports,
    pub trades_1m: u32,
    /// Price change in bps over 1m / 5m (signed).
    pub price_change_1m: i64,
    pub price_change_5m: i64,
    pub real_sol_reserves: Lamports,
    pub holders: u32,
    /// Share of 1m volume from wallets trading both sides repeatedly.
    pub wash_share: Bps,
    /// Price impact for the configured base order size.
    pub base_order_impact: Bps,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Factor {
    pub score: String,
    pub name: String,
    pub contribution: i16,
    pub detail: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Assessment {
    pub safety: u8,
    pub momentum: u8,
    pub liquidity: u8,
    pub opportunity: u8,
    pub hard_rejects: Vec<String>,
    pub factors: Vec<Factor>,
    pub risk_class: RiskClass,
    pub eligible: bool,
    pub ineligible_reasons: Vec<String>,
}

struct Scorer {
    score: &'static str,
    value: i32,
    factors: Vec<Factor>,
}

impl Scorer {
    fn new(score: &'static str, base: i32) -> Self {
        Self { score, value: base, factors: Vec::new() }
    }
    fn add(&mut self, name: &'static str, pts: i32, detail: impl Into<String>) {
        if pts != 0 {
            self.value += pts;
            self.factors.push(Factor { score: self.score.into(), name: name.into(), contribution: pts as i16, detail: detail.into() });
        }
    }
    fn done(self) -> (u8, Vec<Factor>) {
        (self.value.clamp(0, 100) as u8, self.factors)
    }
}

const SOL: u64 = 1_000_000_000;

fn pct(b: Bps) -> String {
    format!("{}.{:02}%", b.0 / 100, b.0 % 100)
}

pub fn assess(cfg: &TradingConfig, f: &TokenFacts, m: &MarketStats) -> Assessment {
    let sc = &cfg.scoring;
    let sw = &cfg.switches;
    let mut rejects = Vec::new();

    // ---- hard rejects (always)
    if f.mint_blacklisted {
        rejects.push("mint blacklisted".to_string());
    }
    if f.creator_blacklisted {
        rejects.push("creator blacklisted".to_string());
    }
    if f.freeze_authority_present {
        rejects.push("freeze authority present".to_string());
    }
    if f.mint_authority_present {
        rejects.push("mint authority present".to_string());
    }
    for ext in &f.dangerous_extensions {
        rejects.push(format!("dangerous token extension: {ext}"));
    }
    // ---- hard rejects (anti-rug filter)
    if sw.anti_rug {
        if f.top10_concentration > sc.max_top10_concentration {
            rejects.push(format!("top-10 holders {} > {}", pct(f.top10_concentration), pct(sc.max_top10_concentration)));
        }
        if f.creator_holding > sc.max_creator_holding {
            rejects.push(format!("creator holds {} > {}", pct(f.creator_holding), pct(sc.max_creator_holding)));
        }
    }
    if sw.creator_analysis && f.creator_prior_rugs > sc.max_creator_prior_rugs {
        rejects.push(format!("creator has {} prior rugs", f.creator_prior_rugs));
    }

    // ---- safety
    let mut s = Scorer::new("safety", 100);
    s.add("top10_concentration", -((f.top10_concentration.0 / 100) as i32), format!("top-10 hold {}", pct(f.top10_concentration)));
    s.add("creator_holding", -((f.creator_holding.0 / 50) as i32), format!("creator holds {}", pct(f.creator_holding)));
    s.add("wallet_cluster", -((f.cluster_share.0 / 50) as i32), format!("linked wallets hold {}", pct(f.cluster_share)));
    s.add("bundled_launch", -((f.same_slot_buy_share.0 / 50) as i32), format!("same-slot buys {}", pct(f.same_slot_buy_share)));
    s.add("wash_trading", -((m.wash_share.0 / 100) as i32), format!("wash-like volume {}", pct(m.wash_share)));
    if sw.creator_analysis {
        s.add("creator_rugs", -25 * f.creator_prior_rugs.min(4) as i32, format!("{} prior rugs", f.creator_prior_rugs));
        if f.creator_prior_launches > 20 {
            s.add("serial_launcher", -10, format!("{} prior launches", f.creator_prior_launches));
        }
        if f.creator_sold {
            s.add("creator_sold", -15, "creator has sold");
        }
    }
    if !f.sell_observed && m.age_secs > 60 {
        s.add("no_sells_observed", -15, "no successful non-creator sell yet");
    }
    if !rejects.is_empty() {
        s.add("hard_reject", -100, rejects.join("; "));
    }
    let (safety, mut factors) = s.done();

    // ---- momentum
    let mut mo = Scorer::new("momentum", 0);
    mo.add("unique_buyers_1m", (m.unique_buyers_1m.min(40) as i32) * 3 / 4, format!("{} unique buyers / 1m", m.unique_buyers_1m));
    let accel = m.unique_buyers_1m as i32 * 5 - m.unique_buyers_5m as i32;
    mo.add("buyer_acceleration", (accel / 2).clamp(-15, 15), format!("1m×5 vs 5m buyers: {accel:+}"));
    let total = m.buy_volume_1m.0 + m.sell_volume_1m.0;
    if let Some(share) = (m.buy_volume_1m.0 * 100).checked_div(total) {
        let buy_share = share as i32;
        mo.add("buy_sell_imbalance", (buy_share - 50) / 2, format!("buys {buy_share}% of 1m volume"));
    }
    mo.add("price_trend_1m", (m.price_change_1m / 200).clamp(-15, 20) as i32, format!("1m price {:+} bps", m.price_change_1m));
    mo.add("price_trend_5m", (m.price_change_5m / 500).clamp(-10, 10) as i32, format!("5m price {:+} bps", m.price_change_5m));
    mo.add("trade_frequency", (m.trades_1m.min(60) / 4) as i32, format!("{} trades / 1m", m.trades_1m));
    mo.add("wash_discount", -((m.wash_share.0 / 200) as i32), "wash-like volume discounted");
    let (momentum, mf) = if sw.momentum_analysis { mo.done() } else { (50, vec![]) };
    factors.extend(mf);

    // ---- liquidity quality
    let mut l = Scorer::new("liquidity", 0);
    let reserves_sol_x10 = (m.real_sol_reserves.0 * 10 / SOL) as i32;
    l.add("curve_reserves", reserves_sol_x10.min(60), format!("{} SOL real reserves", m.real_sol_reserves));
    l.add("vol_1m", ((total * 10 / SOL) as i32).min(20), format!("{} SOL 1m volume", Lamports(total)));
    l.add("holders", (m.holders.min(200) / 10) as i32, format!("{} holders", m.holders));
    l.add("impact", -((m.base_order_impact.0 / 25) as i32), format!("base order impact {}", pct(m.base_order_impact)));
    let (liquidity, lf) = l.done();
    factors.extend(lf);

    let opportunity = if rejects.is_empty() {
        ((safety as u32 * sc.weight_safety as u32 + momentum as u32 * sc.weight_momentum as u32 + liquidity as u32 * sc.weight_liquidity as u32) / 100) as u8
    } else {
        0
    };

    let risk_class = if safety >= 85 {
        RiskClass::Low
    } else if safety >= sc.min_safety {
        RiskClass::Medium
    } else {
        RiskClass::High
    };

    let mut why = rejects.clone();
    for (name, v, min) in [
        ("safety", safety, sc.min_safety),
        ("momentum", momentum, sc.min_momentum),
        ("liquidity", liquidity, sc.min_liquidity),
        ("opportunity", opportunity, sc.min_opportunity),
    ] {
        if v < min {
            why.push(format!("{name} {v} < {min}"));
        }
    }

    Assessment { safety, momentum, liquidity, opportunity, hard_rejects: rejects, factors, risk_class, eligible: why.is_empty(), ineligible_reasons: why }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn clean() -> TokenFacts {
        TokenFacts {
            top10_concentration: Bps::percent(15),
            creator_holding: Bps::percent(3),
            sell_observed: true,
            ..Default::default()
        }
    }
    fn hot() -> MarketStats {
        MarketStats {
            age_secs: 120,
            unique_buyers_1m: 35,
            unique_buyers_5m: 90,
            buy_volume_1m: Lamports(4 * SOL),
            sell_volume_1m: Lamports(SOL),
            trades_1m: 80,
            price_change_1m: 2_500,
            price_change_5m: 6_000,
            real_sol_reserves: Lamports(12 * SOL),
            holders: 150,
            wash_share: Bps(0),
            base_order_impact: Bps(30),
        }
    }

    #[test]
    fn clean_hot_token_is_eligible_and_explained() {
        let a = assess(&TradingConfig::default(), &clean(), &hot());
        assert!(a.eligible, "{a:#?}");
        assert!(a.safety >= 70 && a.momentum >= 50 && a.liquidity >= 40);
        assert!(a.factors.iter().any(|f| f.name == "unique_buyers_1m"));
        let sum: i32 = a.factors.iter().filter(|f| f.score == "safety").map(|f| f.contribution as i32).sum();
        assert_eq!(100 + sum, a.safety as i32, "safety factors explain the score");
    }

    #[test]
    fn freeze_authority_is_hard_reject_even_with_anti_rug_off() {
        let mut c = TradingConfig::default();
        c.switches.anti_rug = false;
        let f = TokenFacts { freeze_authority_present: true, ..clean() };
        let a = assess(&c, &f, &hot());
        assert!(!a.eligible);
        assert_eq!(a.opportunity, 0);
        assert_eq!(a.safety, 0);
    }

    #[test]
    fn concentration_and_creator_history_reject() {
        let c = TradingConfig::default();
        let a = assess(&c, &TokenFacts { top10_concentration: Bps::percent(45), ..clean() }, &hot());
        assert!(a.hard_rejects.iter().any(|r| r.contains("top-10")));
        let a = assess(&c, &TokenFacts { creator_prior_rugs: 2, ..clean() }, &hot());
        assert!(!a.eligible);
    }

    #[test]
    fn dead_market_not_eligible() {
        let m = MarketStats { age_secs: 300, real_sol_reserves: Lamports(SOL / 2), ..Default::default() };
        let a = assess(&TradingConfig::default(), &clean(), &m);
        assert!(!a.eligible);
        assert!(a.momentum < 20);
    }

    #[test]
    fn bundled_and_wash_traded_launch_scores_lower() {
        let base = assess(&TradingConfig::default(), &clean(), &hot());
        let f = TokenFacts { same_slot_buy_share: Bps::percent(25), cluster_share: Bps::percent(10), ..clean() };
        let m = MarketStats { wash_share: Bps::percent(40), ..hot() };
        let bad = assess(&TradingConfig::default(), &f, &m);
        assert!(bad.safety + 20 < base.safety);
        assert_eq!(bad.risk_class, RiskClass::High);
    }
}
