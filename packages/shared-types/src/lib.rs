//! Shared domain types.
//!
//! Money is always integer: SOL in lamports (`Lamports`), SPL tokens in base
//! units (`TokenAmount`). Ratios are basis points (`Bps`, 1 bp = 0.01%).
//! Floats are deliberately absent from this crate.

use serde::{Deserialize, Serialize};
use std::fmt;

pub const LAMPORTS_PER_SOL: u64 = 1_000_000_000;
pub const BPS_DENOMINATOR: u64 = 10_000;

/// SOL amount in lamports.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct Lamports(pub u64);

impl Lamports {
    pub const ZERO: Lamports = Lamports(0);

    pub const fn from_sol_milli(milli_sol: u64) -> Self {
        Lamports(milli_sol * (LAMPORTS_PER_SOL / 1_000))
    }
    pub fn checked_add(self, o: Lamports) -> Option<Lamports> {
        self.0.checked_add(o.0).map(Lamports)
    }
    pub fn saturating_add(self, o: Lamports) -> Lamports {
        Lamports(self.0.saturating_add(o.0))
    }
    pub fn saturating_sub(self, o: Lamports) -> Lamports {
        Lamports(self.0.saturating_sub(o.0))
    }
    /// `self * bps / 10_000`, rounded down, computed in u128 (no overflow).
    pub fn mul_bps(self, bps: Bps) -> Lamports {
        Lamports(((self.0 as u128 * bps.0 as u128) / BPS_DENOMINATOR as u128) as u64)
    }
    pub fn min(self, o: Lamports) -> Lamports {
        if self <= o { self } else { o }
    }
}

impl fmt::Display for Lamports {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}.{:09} SOL", self.0 / LAMPORTS_PER_SOL, self.0 % LAMPORTS_PER_SOL)
    }
}

/// Signed lamport amount for PnL.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct SignedLamports(pub i64);

impl SignedLamports {
    pub fn diff(a: Lamports, b: Lamports) -> SignedLamports {
        SignedLamports(a.0 as i64 - b.0 as i64)
    }
    pub fn is_loss(self) -> bool {
        self.0 < 0
    }
}

/// SPL token amount in base units.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct TokenAmount(pub u64);

impl TokenAmount {
    pub fn mul_bps(self, bps: Bps) -> TokenAmount {
        TokenAmount(((self.0 as u128 * bps.0 as u128) / BPS_DENOMINATOR as u128) as u64)
    }
    pub fn saturating_sub(self, o: TokenAmount) -> TokenAmount {
        TokenAmount(self.0.saturating_sub(o.0))
    }
}

/// Basis points. 10_000 = 100%.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Default, Serialize, Deserialize)]
#[serde(transparent)]
pub struct Bps(pub u32);

impl Bps {
    pub const HUNDRED_PERCENT: Bps = Bps(10_000);
    pub const fn percent(p: u32) -> Bps {
        Bps(p * 100)
    }
}

/// Signed change in basis points (e.g. PnL %, drawdown).
pub type SignedBps = i64;

/// `(now - base) / base` in bps, signed. Returns 0 if base is 0.
pub fn change_bps(base: Lamports, now: Lamports) -> SignedBps {
    if base.0 == 0 {
        return 0;
    }
    ((now.0 as i128 - base.0 as i128) * BPS_DENOMINATOR as i128 / base.0 as i128) as i64
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TradingMode {
    Paper,
    Live,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Side {
    Buy,
    Sell,
}

/// Base58 Solana address, kept as a string at this layer.
pub type Address = String;

/// Milliseconds since Unix epoch. The engine is given time explicitly so it
/// is deterministic under test and replay.
pub type TimestampMs = i64;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum RiskClass {
    Low,
    Medium,
    High,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bps_math_does_not_overflow() {
        let big = Lamports(u64::MAX);
        assert_eq!(big.mul_bps(Bps::HUNDRED_PERCENT), big);
        assert_eq!(Lamports(1_000).mul_bps(Bps(2_500)), Lamports(250));
    }

    #[test]
    fn change_bps_signed() {
        assert_eq!(change_bps(Lamports(100), Lamports(120)), 2_000);
        assert_eq!(change_bps(Lamports(100), Lamports(80)), -2_000);
        assert_eq!(change_bps(Lamports(0), Lamports(80)), 0);
    }

    #[test]
    fn display_sol() {
        assert_eq!(Lamports(1_500_000_000).to_string(), "1.500000000 SOL");
        assert_eq!(Lamports::from_sol_milli(250), Lamports(250_000_000));
    }
}
