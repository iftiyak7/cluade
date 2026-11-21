//! Pump.fun bonding-curve math (constant product over *virtual* reserves).
//!
//! All arithmetic is integer (u128 intermediates) and rounds against the
//! trader, mirroring the on-chain program, so quotes are never optimistic.
//!
//! Fees are a parameter rather than a constant: Pump.fun's fee schedule
//! (protocol + creator fee, market-cap tiers) has changed over time and must
//! be read from the on-chain global/fee-config accounts at runtime.

use pf_types::{Bps, Lamports, TokenAmount, BPS_DENOMINATOR};

/// Launch parameters observed for standard Pump.fun tokens (6 decimals).
pub const INITIAL_VIRTUAL_SOL: u64 = 30_000_000_000;
pub const INITIAL_VIRTUAL_TOKENS: u64 = 1_073_000_000_000_000;
pub const INITIAL_REAL_TOKENS: u64 = 793_100_000_000_000;
pub const TOKEN_TOTAL_SUPPLY: u64 = 1_000_000_000_000_000;

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
    #[error("amount must be > 0")]
    ZeroAmount,
    #[error("insufficient reserves")]
    InsufficientReserves,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct BuyQuote {
    /// Total SOL leaving the wallet (input + fee).
    pub sol_cost: Lamports,
    pub fee: Lamports,
    pub tokens_out: TokenAmount,
    /// Price impact vs. pre-trade spot price, in bps.
    pub price_impact: Bps,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct SellQuote {
    /// SOL received after fee.
    pub sol_out: Lamports,
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

    /// Value of `tokens` if sold now, after fee (what a position is worth).
    pub fn liquidation_value(&self, tokens: TokenAmount, fee: Bps) -> Lamports {
        if tokens.0 == 0 || self.complete {
            return Lamports::ZERO;
        }
        self.quote_sell(tokens, fee).map(|q| q.sol_out).unwrap_or(Lamports::ZERO)
    }

    /// Quote spending `sol_in` (fee charged on top, like the program's
    /// `buy_exact_sol_in` path: fee is deducted from the input).
    pub fn quote_buy(&self, sol_in: Lamports, fee_bps: Bps) -> Result<BuyQuote, CurveError> {
        if self.complete {
            return Err(CurveError::Complete);
        }
        if sol_in.0 == 0 {
            return Err(CurveError::ZeroAmount);
        }
        // fee taken out of the input, rounded up (against trader)
        let fee = ceil_div(sol_in.0 as u128 * fee_bps.0 as u128, BPS_DENOMINATOR as u128) as u64;
        let net = sol_in.0 - fee;
        let vs = self.virtual_sol_reserves as u128;
        let vt = self.virtual_token_reserves as u128;
        let k = vs * vt;
        let new_vs = vs + net as u128;
        // new token reserves rounded UP -> tokens_out rounded DOWN
        let new_vt = ceil_div(k, new_vs);
        let mut out = (vt - new_vt) as u64;
        out = out.min(self.real_token_reserves);
        if out == 0 {
            return Err(CurveError::InsufficientReserves);
        }
        // impact: executed avg price vs spot. avg = net/out, spot = vs/vt.
        // impact = (net*vt)/(out*vs) - 1
        let impact = ((net as u128 * vt * BPS_DENOMINATOR as u128) / (out as u128 * vs)).saturating_sub(BPS_DENOMINATOR as u128);
        Ok(BuyQuote { sol_cost: sol_in, fee: Lamports(fee), tokens_out: TokenAmount(out), price_impact: Bps(impact.min(u32::MAX as u128) as u32) })
    }

    pub fn quote_sell(&self, tokens_in: TokenAmount, fee_bps: Bps) -> Result<SellQuote, CurveError> {
        if self.complete {
            return Err(CurveError::Complete);
        }
        if tokens_in.0 == 0 {
            return Err(CurveError::ZeroAmount);
        }
        let vs = self.virtual_sol_reserves as u128;
        let vt = self.virtual_token_reserves as u128;
        let k = vs * vt;
        let new_vt = vt + tokens_in.0 as u128;
        let new_vs = ceil_div(k, new_vt);
        let gross = (vs - new_vs) as u64;
        let gross = gross.min(self.real_sol_reserves);
        if gross == 0 {
            return Err(CurveError::InsufficientReserves);
        }
        let fee = ceil_div(gross as u128 * fee_bps.0 as u128, BPS_DENOMINATOR as u128) as u64;
        // impact = 1 - (gross*vt)/(tokens*vs)
        let ratio = (gross as u128 * vt * BPS_DENOMINATOR as u128) / (tokens_in.0 as u128 * vs);
        let impact = (BPS_DENOMINATOR as u128).saturating_sub(ratio);
        Ok(SellQuote { sol_out: Lamports(gross - fee), fee: Lamports(fee), tokens_in, price_impact: Bps(impact as u32) })
    }

    /// Apply a buy to the state (used by the paper-trading simulator).
    pub fn apply_buy(&mut self, sol_in: Lamports, fee_bps: Bps) -> Result<BuyQuote, CurveError> {
        let q = self.quote_buy(sol_in, fee_bps)?;
        let net = q.sol_cost.0 - q.fee.0;
        self.virtual_sol_reserves += net;
        self.real_sol_reserves += net;
        self.virtual_token_reserves -= q.tokens_out.0;
        self.real_token_reserves -= q.tokens_out.0;
        if self.real_token_reserves == 0 {
            self.complete = true;
        }
        Ok(q)
    }

    pub fn apply_sell(&mut self, tokens: TokenAmount, fee_bps: Bps) -> Result<SellQuote, CurveError> {
        let q = self.quote_sell(tokens, fee_bps)?;
        let gross = q.sol_out.0 + q.fee.0;
        self.virtual_sol_reserves -= gross;
        self.real_sol_reserves -= gross;
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
    const FEE: Bps = Bps(100); // 1% — placeholder; real fee read from chain

    #[test]
    fn fresh_curve_market_cap_about_28_sol() {
        let c = CurveState::fresh();
        // 30 SOL * 1e15 / 1.073e15 ≈ 27.96 SOL
        assert_eq!(c.market_cap().0 / 1_000_000, 27_958);
    }

    #[test]
    fn buy_then_sell_round_trip_loses_only_fees_and_rounding() {
        let mut c = CurveState::fresh();
        let before = c;
        let b = c.apply_buy(Lamports(1_000_000_000), FEE).unwrap();
        assert!(b.tokens_out.0 > 0);
        let s = c.apply_sell(b.tokens_out, FEE).unwrap();
        // got back ≈ 1 SOL - 1% - 1% of ~0.99
        assert!(s.sol_out.0 < 1_000_000_000 - 19_000_000);
        assert!(s.sol_out.0 > 1_000_000_000 - 21_000_000);
        // curve token reserves restored exactly; SOL reserves never under-collateralised
        assert_eq!(c.virtual_token_reserves, before.virtual_token_reserves);
        assert!(c.virtual_sol_reserves >= before.virtual_sol_reserves);
    }

    #[test]
    fn constant_product_never_decreases() {
        let mut c = CurveState::fresh();
        let k0 = c.virtual_sol_reserves as u128 * c.virtual_token_reserves as u128;
        for i in 1..50u64 {
            c.apply_buy(Lamports(i * 37_000_000), Bps(0)).unwrap();
            let k = c.virtual_sol_reserves as u128 * c.virtual_token_reserves as u128;
            assert!(k >= k0, "rounding must favour the pool");
        }
    }

    #[test]
    fn larger_buys_have_larger_impact() {
        let c = CurveState::fresh();
        let small = c.quote_buy(Lamports(10_000_000), FEE).unwrap();
        let big = c.quote_buy(Lamports(5_000_000_000), FEE).unwrap();
        assert!(big.price_impact > small.price_impact);
        assert!(small.price_impact.0 < 10, "0.01 SOL buy should be <0.1% impact");
    }

    #[test]
    fn buy_capped_at_real_reserves_and_completes() {
        let mut c = CurveState::fresh();
        let q = c.apply_buy(Lamports(500_000_000_000), Bps(0)).unwrap();
        assert_eq!(q.tokens_out.0, INITIAL_REAL_TOKENS);
        assert!(c.complete);
        assert_eq!(c.quote_buy(Lamports(1), Bps(0)), Err(CurveError::Complete));
    }

    #[test]
    fn slippage_min_out() {
        assert_eq!(min_out_with_slippage(1_000, Bps(500)), 950);
        assert_eq!(min_out_with_slippage(1_000, Bps(0)), 1_000);
    }
}
