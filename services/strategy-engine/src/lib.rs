//! Entry decisions (Building 05 buy modes + Building 03 discovery filters).
//!
//! The strategy only *proposes*; the risk engine decides and sizes.
//! Rejections are split into permanent (never revisit this token) and
//! waiting (conditions may still be met later).

use pf_config::{BuyMode, TradingConfig};
use pf_intel::Assessment;
use pf_market::TokenState;
use pf_solana::token::MintInfo;
use pf_types::{Lamports, RiskClass};
use serde::Serialize;

#[derive(Debug, Clone, PartialEq, Serialize)]
pub enum EntryDecision {
    Enter { size: Lamports, risk_class: RiskClass, requires_approval: bool, reasons: Vec<String> },
    Wait { reasons: Vec<String> },
    Reject { reasons: Vec<String> },
}

impl EntryDecision {
    pub fn is_enter(&self) -> bool {
        matches!(self, EntryDecision::Enter { .. })
    }
}

/// `mint` is `None` until the mint account has been fetched; we never
/// enter without it.
pub fn evaluate_entry(cfg: &TradingConfig, t: &TokenState, mint: Option<&MintInfo>, a: &Assessment, now_ms: i64) -> EntryDecision {
    let e = &cfg.entry;
    let mut reject = Vec::new();
    if e.token_blacklist.contains(&t.mint) {
        reject.push("token blacklisted".to_string());
    }
    if e.creator_blacklist.contains(&t.creator) {
        reject.push("creator blacklisted".to_string());
    }
    if e.require_sol_quote && !t.is_sol_quoted() {
        reject.push(format!("quote asset {} is not SOL", t.quote_mint));
    }
    if t.mayhem && !e.allow_mayhem {
        reject.push("mayhem-mode curve".to_string());
    }
    if t.complete {
        reject.push("curve complete / migrated".to_string());
    }
    let age = t.age_secs(now_ms);
    if age > e.max_age_secs {
        reject.push(format!("age {age}s > max {}s", e.max_age_secs));
    }
    if let Some(m) = mint {
        reject.extend(m.red_flags());
    }
    // Authority/extension/blacklist rejects from scoring are permanent too.
    reject.extend(a.hard_rejects.iter().filter(|r| r.contains("authority") || r.contains("extension") || r.contains("blacklist") || r.contains("prior rugs")).cloned());
    if !reject.is_empty() {
        return EntryDecision::Reject { reasons: reject };
    }

    let mut wait = Vec::new();
    if mint.is_none() {
        wait.push("mint account not inspected yet".to_string());
    }
    let s = t.stats(now_ms, cfg.sizing.base_size);
    let sniper = e.buy_mode == BuyMode::InstantSniper;
    if !sniper {
        if age < e.min_age_secs {
            wait.push(format!("age {age}s < {}s", e.min_age_secs));
        }
        if t.n_trades < e.min_trades as u64 {
            wait.push(format!("{} trades < {}", t.n_trades, e.min_trades));
        }
        if s.unique_buyers_5m < e.min_unique_buyers {
            wait.push(format!("{} unique buyers < {}", s.unique_buyers_5m, e.min_unique_buyers));
        }
        if s.buy_volume_1m.0 + s.sell_volume_1m.0 < e.min_volume_1m.0 {
            wait.push(format!("1m volume {} < {}", Lamports(s.buy_volume_1m.0 + s.sell_volume_1m.0), e.min_volume_1m));
        }
    }
    if s.real_sol_reserves < e.min_liquidity {
        wait.push(format!("liquidity {} < {}", s.real_sol_reserves, e.min_liquidity));
    }
    if t.curve.market_cap() > e.max_market_cap {
        wait.push(format!("market cap {} > {}", t.curve.market_cap(), e.max_market_cap));
    }
    if !a.eligible {
        wait.extend(a.ineligible_reasons.iter().cloned());
    }
    let mut size = cfg.sizing.base_size;
    match e.buy_mode {
        BuyMode::Conservative => {
            if a.safety < e.conservative_min_safety {
                wait.push(format!("conservative: safety {} < {}", a.safety, e.conservative_min_safety));
            }
            size = Lamports(size.0 / 2);
        }
        BuyMode::MomentumConfirmation => {
            if s.price_change_1m < e.momentum_min_price_change_1m_bps {
                wait.push(format!("momentum: 1m change {} bps < {}", s.price_change_1m, e.momentum_min_price_change_1m_bps));
            }
            if (s.unique_buyers_1m as i64) * 5 <= s.unique_buyers_5m as i64 && age >= 60 {
                wait.push("momentum: buyer arrival is not accelerating".to_string());
            }
        }
        _ => {}
    }
    if !wait.is_empty() {
        return EntryDecision::Wait { reasons: wait };
    }
    EntryDecision::Enter {
        size,
        risk_class: a.risk_class,
        requires_approval: e.buy_mode == BuyMode::Manual,
        reasons: vec![format!("safety {} momentum {} liquidity {} opportunity {}", a.safety, a.momentum, a.liquidity, a.opportunity)],
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pf_events::{TokenDiscovered, TradeObserved};
    use pf_intel::assess;
    use pf_market::{MarketBook, SOL_QUOTE};
    use pf_solana::borsh::Pubkey;
    use pf_solana::curve::{CurveState, FeeSchedule};
    use pf_types::{Bps, Side, TokenAmount};

    fn create(mint: &str, mayhem: bool, quote: &str) -> TokenDiscovered {
        TokenDiscovered {
            mint: mint.into(), bonding_curve: "BC".into(), creator: "CR".into(), name: "n".into(), symbol: "s".into(), uri: "u".into(),
            slot: 100, signature: "s".into(), quote_mint: quote.into(), token_program: None, mayhem_mode: mayhem, creator_fee_bps: Some(30),
            block_time: Some(1_000), received_at_us: 0, decoded_at_us: 0,
        }
    }

    fn buy(c: &mut CurveState, mint: &str, who: &str, lamports: u64, slot: u64, bt: i64) -> TradeObserved {
        let q = c.apply_buy(Lamports(lamports), FeeSchedule::new(95, 30)).unwrap();
        TradeObserved {
            mint: mint.into(), signature: format!("{who}{slot}"), trader: who.into(), side: Side::Buy, quote_amount: q.curve_input.0,
            token_amount: q.tokens_out, virtual_quote_reserves: c.virtual_sol_reserves, virtual_token_reserves: c.virtual_token_reserves,
            real_quote_reserves: c.real_sol_reserves, real_token_reserves: c.real_token_reserves, quote_mint: SOL_QUOTE.into(),
            fee_bps: 95, creator_fee_bps: 30, fees: 0, ix_name: "buy".into(), slot, block_time: Some(bt),
        }
    }

    fn clean_mint() -> MintInfo {
        MintInfo { token_2022: true, mint_authority: None, freeze_authority: None, supply: 1, decimals: 6, is_initialized: true, extensions: vec![18, 19], metadata_name: None }
    }

    /// A lively token: 30 distinct buyers over 30 s.
    fn hot_book(mayhem: bool, quote: &str) -> MarketBook {
        let mut b = MarketBook::default();
        b.on_create(&create("M", mayhem, quote));
        let mut c = CurveState::fresh();
        for i in 0..30u64 {
            b.on_trade(&buy(&mut c, "M", &format!("W{i}"), 150_000_000, 101 + i, 1_000 + i as i64));
        }
        b
    }

    fn decide(cfg: &TradingConfig, b: &MarketBook, mint: Option<&MintInfo>, now_ms: i64) -> EntryDecision {
        let t = &b.tokens["M"];
        let mut facts = t.facts();
        facts.sell_observed = true;
        let a = assess(cfg, &facts, &t.stats(now_ms, cfg.sizing.base_size));
        evaluate_entry(cfg, t, mint, &a, now_ms)
    }

    #[test]
    fn enters_lively_clean_token() {
        let cfg = TradingConfig::default();
        let b = hot_book(false, SOL_QUOTE);
        let d = decide(&cfg, &b, Some(&clean_mint()), 1_030_000);
        assert!(d.is_enter(), "{d:?}");
    }

    #[test]
    fn waits_for_mint_inspection_and_age() {
        let cfg = TradingConfig::default();
        let b = hot_book(false, SOL_QUOTE);
        assert!(matches!(decide(&cfg, &b, None, 1_030_000), EntryDecision::Wait { .. }));
        assert!(matches!(decide(&cfg, &b, Some(&clean_mint()), 1_005_000), EntryDecision::Wait { .. }), "too young");
    }

    #[test]
    fn permanent_rejects() {
        let cfg = TradingConfig::default();
        assert!(matches!(decide(&cfg, &hot_book(true, SOL_QUOTE), Some(&clean_mint()), 1_030_000), EntryDecision::Reject { .. }));
        assert!(matches!(decide(&cfg, &hot_book(false, "USDC"), Some(&clean_mint()), 1_030_000), EntryDecision::Reject { .. }));
        let mut bad = clean_mint();
        bad.extensions.push(14);
        assert!(matches!(decide(&cfg, &hot_book(false, SOL_QUOTE), Some(&bad), 1_030_000), EntryDecision::Reject { .. }));
        bad = clean_mint();
        bad.freeze_authority = Some(Pubkey([1; 32]));
        assert!(matches!(decide(&cfg, &hot_book(false, SOL_QUOTE), Some(&bad), 1_030_000), EntryDecision::Reject { .. }));
        assert!(matches!(decide(&cfg, &hot_book(false, SOL_QUOTE), Some(&clean_mint()), 1_000_000 + 16 * 60_000), EntryDecision::Reject { .. }), "too old");
    }

    #[test]
    fn buy_modes() {
        let b = hot_book(false, SOL_QUOTE);
        let mut cfg = TradingConfig::default();
        cfg.entry.buy_mode = BuyMode::Manual;
        assert!(matches!(decide(&cfg, &b, Some(&clean_mint()), 1_030_000), EntryDecision::Enter { requires_approval: true, .. }));
        cfg.entry.buy_mode = BuyMode::Conservative;
        cfg.entry.conservative_min_safety = 100;
        assert!(matches!(decide(&cfg, &b, Some(&clean_mint()), 1_030_000), EntryDecision::Wait { .. }));
        cfg.entry.buy_mode = BuyMode::InstantSniper;
        let young = decide(&cfg, &b, Some(&clean_mint()), 1_005_000);
        assert!(!matches!(young, EntryDecision::Reject { .. }), "sniper ignores min age: {young:?}");
        let _ = (Bps(0), TokenAmount(0));
    }
}
