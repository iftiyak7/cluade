//! Pump.fun bonding-curve math (constant product over *virtual* reserves).
//!
//! Formulas reproduce mainnet to the lamport/base unit (verified against
//! real TradeEvents in `tests/fixtures`, 2026-11-21):
//!
//! * **Buy** with curve input `s` (`sol_amount` in the event): tokens are
//!   priced on `s - 1`: `tokens = floor(vt·(s-1) / (vs + s - 1))`; the curve
//!   receives `s`. Fees are charged **on top**: each component
//!   `ceil(s · bps / 10_000)`, rounded up separately.
//! * **Sell** of `t` tokens: `gross = floor(vs·t / (vt + t))`; fees are
//!   deducted from `gross` with the same per-component rounding.
//!
//! Fees vary per token (creator fee is configurable per curve, up to
//! 100 bps), so always build the [`FeeSchedule`] from the curve/event, never
//! from a constant.

use pf_types::{Bps, Lamports, TokenAmount, BPS_DENOMINATOR};

/// Standard (non-mayhem) launch parameters, read from the Global account on
/// 2026-11-21. Mayhem-mode curves use different virtual reserves.
pub const INITIAL_VIRTUAL_SOL: u64 = 30_000_000_000;
pub const INITIAL_VIRTUAL_TOKENS: u64 = 1_073_000_000_000_000;
pub const INITIAL_REAL_TOKENS: u64 = 793_100_000_000_000;
pub const TOKEN_TOTAL_SUPPLY: u64 = 1_000_000_000_000_000;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct FeeSchedule {
    pub protocol_bps: u32,
    pub creator_bps: u32,
    /// Any further trader-paid component (e.g. cashback fee).
    pub other_bps: u32,
}

impl FeeSchedule {
    pub const fn new(protocol_bps: u32, creator_bps: u32) -> Self {
        Self { protocol_bps, creator_bps, other_bps: 0 }
    }
    pub const ZERO: FeeSchedule = FeeSchedule::new(0, 0);
    pub fn total_bps(&self) -> u32 {
        self.protocol_bps + self.creator_bps + self.other_bps
    }
    /// Sum of per-component fees, each rounded up (as the program does).
    pub fn fees_on(&self, amount: u64) -> u64 {
        [self.protocol_bps, self.creator_bps, self.other_bps]
            .iter()
            .map(|b| ceil_div(amount as u128 * *b as u128, BPS_DENOMINATOR as u128) as u64)
            .sum()
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct CurveState {
    pub virtual_sol_reserves: u64,
    pub virtual_token_reserves: u64,
    pub real_sol_reserves: u64,
    pub real_token_reserves: u64,
    pub complete: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum CurveError {
    #[error("bonding curve is complete (migrated); trade on the AMM instead")]
    Complete,
    #[error("amount too small")]
    ZeroAmount,
    #[error("insufficient reserves")]
    InsufficientReserves,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BuyQuote {
    /// Total SOL leaving the wallet for this trade (curve input + fees).
    /// Excludes rent for a new token account and network/priority fees.
    pub sol_cost: Lamports,
    /// SOL credited to the curve (`sol_amount` in the TradeEvent).
    pub curve_input: Lamports,
    pub fee: Lamports,
    pub tokens_out: TokenAmount,
    /// Execution price vs. pre-trade spot price, in bps (fees excluded).
    pub price_impact: Bps,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SellQuote {
    /// SOL received after fees.
    pub sol_out: Lamports,
    /// SOL leaving the curve (`sol_amount` in the TradeEvent).
    pub gross: Lamports,
    pub fee: Lamports,
    pub tokens_in: TokenAmount,
    pub price_impact: Bps,
}

impl CurveState {
    pub fn fresh() -> Self {
        Self {
            virtual_sol_reserves: INITIAL_VIRTUAL_SOL,
            virtual_token_reserves: INITIAL_VIRTUAL_TOKENS,
            real_sol_reserves: 0,
            real_token_reserves: INITIAL_REAL_TOKENS,
            complete: false,
        }
    }

    /// Spot price as lamports per 1e6 base units (1 whole token at 6 dp),
    /// scaled by 1e9 to keep precision in integers.
    pub fn spot_price_scaled(&self) -> u128 {
        (self.virtual_sol_reserves as u128 * 1_000_000 * 1_000_000_000) / self.virtual_token_reserves as u128
    }

    /// Market cap in lamports at spot price (total supply × price).
    pub fn market_cap(&self) -> Lamports {
        Lamports(((self.virtual_sol_reserves as u128 * TOKEN_TOTAL_SUPPLY as u128) / self.virtual_token_reserves as u128) as u64)
    }

    /// Value of `tokens` if sold now, after fees.
    pub fn liquidation_value(&self, tokens: TokenAmount, fees: FeeSchedule) -> Lamports {
        if tokens.0 == 0 || self.complete {
            return Lamports::ZERO;
        }
        self.quote_sell(tokens, fees).map(|q| q.sol_out).unwrap_or(Lamports::ZERO)
    }

    /// Tokens delivered for curve input `s` (chain prices on `s - 1`).
    pub fn tokens_for_input(&self, s: u64) -> u64 {
        if s < 2 {
            return 0;
        }
        let x = (s - 1) as u128;
        let vt = self.virtual_token_reserves as u128;
        let vs = self.virtual_sol_reserves as u128;
        (vt * x / (vs + x)) as u64
    }

    /// Quote spending at most `budget` lamports (curve input + fees).
    pub fn quote_buy(&self, budget: Lamports, fees: FeeSchedule) -> Result<BuyQuote, CurveError> {
        if self.complete {
            return Err(CurveError::Complete);
        }
        let total = |s: u64| s.saturating_add(fees.fees_on(s));
        let tb = fees.total_bps() as u128;
        let mut s = (budget.0 as u128 * BPS_DENOMINATOR as u128 / (BPS_DENOMINATOR as u128 + tb)) as u64;
        while s > 0 && total(s) > budget.0 {
            s -= 1;
        }
        while total(s + 1) <= budget.0 {
            s += 1;
        }
        let mut tokens = self.tokens_for_input(s);
        if tokens == 0 {
            return Err(CurveError::ZeroAmount);
        }
        if tokens > self.real_token_reserves {
            // Buying out the curve: pay only for the remaining tokens,
            // `ceil(vs·t / (vt − t))` (no +1 — verified on a mainnet buyout).
            tokens = self.real_token_reserves;
            if tokens == 0 {
                return Err(CurveError::InsufficientReserves);
            }
            let vs = self.virtual_sol_reserves as u128;
            let vt = self.virtual_token_reserves as u128;
            s = ceil_div(vs * tokens as u128, vt - tokens as u128) as u64;
        }
        let fee = fees.fees_on(s);
        let vs = self.virtual_sol_reserves as u128;
        let vt = self.virtual_token_reserves as u128;
        let impact = ((s as u128 * vt * BPS_DENOMINATOR as u128) / (tokens as u128 * vs)).saturating_sub(BPS_DENOMINATOR as u128);
        Ok(BuyQuote {
            sol_cost: Lamports(s + fee),
            curve_input: Lamports(s),
            fee: Lamports(fee),
            tokens_out: TokenAmount(tokens),
            price_impact: Bps(impact.min(u32::MAX as u128) as u32),
        })
    }

    /// Quote the `buy` instruction (exact tokens out, capped SOL in):
    /// `sol_amount = ceil(vs·t / (vt − t))`. Note the different rounding
    /// from `buy_exact_quote_in` ([`Self::quote_buy`]), verified on mainnet.
    pub fn quote_buy_exact_tokens(&self, tokens: TokenAmount, fees: FeeSchedule) -> Result<BuyQuote, CurveError> {
        if self.complete {
            return Err(CurveError::Complete);
        }
        if tokens.0 == 0 {
            return Err(CurveError::ZeroAmount);
        }
        if tokens.0 > self.real_token_reserves {
            return Err(CurveError::InsufficientReserves);
        }
        let vs = self.virtual_sol_reserves as u128;
        let vt = self.virtual_token_reserves as u128;
        let s = ceil_div(vs * tokens.0 as u128, vt - tokens.0 as u128) as u64;
        let fee = fees.fees_on(s);
        let impact = ((s as u128 * vt * BPS_DENOMINATOR as u128) / (tokens.0 as u128 * vs)).saturating_sub(BPS_DENOMINATOR as u128);
        Ok(BuyQuote { sol_cost: Lamports(s + fee), curve_input: Lamports(s), fee: Lamports(fee), tokens_out: tokens, price_impact: Bps(impact.min(u32::MAX as u128) as u32) })
    }

    pub fn quote_sell(&self, tokens_in: TokenAmount, fees: FeeSchedule) -> Result<SellQuote, CurveError> {
        if self.complete {
            return Err(CurveError::Complete);
        }
        if tokens_in.0 == 0 {
            return Err(CurveError::ZeroAmount);
        }
        let vs = self.virtual_sol_reserves as u128;
        let vt = self.virtual_token_reserves as u128;
        let t = tokens_in.0 as u128;
        let gross = ((vs * t / (vt + t)) as u64).min(self.real_sol_reserves);
        if gross == 0 {
            return Err(CurveError::InsufficientReserves);
        }
        let fee = fees.fees_on(gross).min(gross);
        let ratio = (gross as u128 * vt * BPS_DENOMINATOR as u128) / (t * vs);
        let impact = (BPS_DENOMINATOR as u128).saturating_sub(ratio);
        Ok(SellQuote { sol_out: Lamports(gross - fee), gross: Lamports(gross), fee: Lamports(fee), tokens_in, price_impact: Bps(impact as u32) })
    }

    /// Apply a buy to the state (paper trading / local curve tracking).
    pub fn apply_buy(&mut self, budget: Lamports, fees: FeeSchedule) -> Result<BuyQuote, CurveError> {
        let q = self.quote_buy(budget, fees)?;
        self.apply_quote(&q);
        Ok(q)
    }

    pub fn apply_quote(&mut self, q: &BuyQuote) {
        self.virtual_sol_reserves += q.curve_input.0;
        self.real_sol_reserves += q.curve_input.0;
        self.virtual_token_reserves -= q.tokens_out.0;
        self.real_token_reserves -= q.tokens_out.0;
        if self.real_token_reserves == 0 {
            self.complete = true;
        }
    }

    pub fn apply_sell(&mut self, tokens: TokenAmount, fees: FeeSchedule) -> Result<SellQuote, CurveError> {
        let q = self.quote_sell(tokens, fees)?;
        self.virtual_sol_reserves -= q.gross.0;
        self.real_sol_reserves -= q.gross.0;
        self.virtual_token_reserves += tokens.0;
        self.real_token_reserves += tokens.0;
        Ok(q)
    }
}

/// Minimum acceptable output given a quote and max slippage.
pub fn min_out_with_slippage(expected: u64, max_slippage: Bps) -> u64 {
    ((expected as u128 * (BPS_DENOMINATOR - max_slippage.0 as u64) as u128) / BPS_DENOMINATOR as u128) as u64
}

fn ceil_div(a: u128, b: u128) -> u128 {
    a.div_ceil(b)
}

#[cfg(test)]
mod tests {
    use super::*;
    const FEE: FeeSchedule = FeeSchedule::new(95, 30);

    #[test]
    fn fresh_curve_market_cap_about_28_sol() {
        assert_eq!(CurveState::fresh().market_cap().0 / 1_000_000, 27_958);
    }

    #[test]
    fn fees_round_up_per_component() {
        // values from a real mainnet sell (fixture sell_routed)
        assert_eq!(FeeSchedule::new(95, 0).fees_on(104_509_016), 992_836);
        assert_eq!(FeeSchedule::new(0, 30).fees_on(104_509_016), 313_528);
        assert_eq!(FEE.fees_on(104_509_016), 992_836 + 313_528);
    }

    #[test]
    fn buy_never_exceeds_budget_and_is_maximal() {
        let c = CurveState::fresh();
        assert_eq!(c.quote_buy(Lamports(3), FEE), Err(CurveError::ZeroAmount));
        for budget in [1_000u64, 10_000_000, 1_234_567_891, 50_000_000_000] {
            let q = c.quote_buy(Lamports(budget), FEE).unwrap();
            assert!(q.sol_cost.0 <= budget, "{budget}");
            let next = q.curve_input.0 + 1;
            assert!(next + FEE.fees_on(next) > budget, "not maximal for {budget}");
        }
    }

    #[test]
    fn buy_then_sell_round_trip_loses_fees() {
        let mut c = CurveState::fresh();
        let before = c;
        let b = c.apply_buy(Lamports(1_000_000_000), FEE).unwrap();
        let s = c.apply_sell(b.tokens_out, FEE).unwrap();
        // ~1.25% on the way in, ~1.25% on the way out
        let lost = 1_000_000_000 - s.sol_out.0;
        assert!((24_000_000..26_000_000).contains(&lost), "lost {lost}");
        assert_eq!(c.virtual_token_reserves, before.virtual_token_reserves);
        assert!(c.virtual_sol_reserves >= before.virtual_sol_reserves, "pool never under-collateralised");
    }

    #[test]
    fn constant_product_never_decreases() {
        let mut c = CurveState::fresh();
        let k0 = c.virtual_sol_reserves as u128 * c.virtual_token_reserves as u128;
        for i in 1..50u64 {
            c.apply_buy(Lamports(i * 37_000_000), FeeSchedule::ZERO).unwrap();
            let k = c.virtual_sol_reserves as u128 * c.virtual_token_reserves as u128;
            assert!(k >= k0);
        }
    }

    #[test]
    fn larger_buys_have_larger_impact() {
        let c = CurveState::fresh();
        let small = c.quote_buy(Lamports(10_000_000), FEE).unwrap();
        let big = c.quote_buy(Lamports(5_000_000_000), FEE).unwrap();
        assert!(big.price_impact > small.price_impact);
        assert!(small.price_impact.0 < 10);
    }

    #[test]
    fn buy_capped_at_real_reserves_and_completes() {
        let mut c = CurveState::fresh();
        let q = c.apply_buy(Lamports(500_000_000_000), FeeSchedule::ZERO).unwrap();
        assert_eq!(q.tokens_out.0, INITIAL_REAL_TOKENS);
        assert!(q.sol_cost.0 < 500_000_000_000, "pays only for remaining supply");
        assert!(c.complete);
        assert_eq!(c.quote_buy(Lamports(10), FeeSchedule::ZERO), Err(CurveError::Complete));
    }

    #[test]
    fn slippage_min_out() {
        assert_eq!(min_out_with_slippage(1_000, Bps(500)), 950);
        assert_eq!(min_out_with_slippage(1_000, Bps(0)), 1_000);
    }
}
