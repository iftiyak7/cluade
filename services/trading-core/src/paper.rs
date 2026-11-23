//! Paper execution model.
//!
//! An order decided at chain tip `T` is assumed to land at slot
//! `T + landing_slots`. It is priced against the first observed curve state
//! at or after that slot (or the latest state once `fill_timeout_ms` passes
//! with no trades). If the price moved beyond the slippage tolerance the
//! order **fails** like a real transaction would, and the network fee is
//! still charged.
//!
//! Known optimism: our simulated trades do not move the real curve, so
//! later valuations ignore our own price impact (small at default sizes).

use pf_market::TokenState;
use pf_solana::curve::min_out_with_slippage;
use pf_types::{Bps, Lamports, TokenAmount};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum PaperSide {
    Buy { budget: Lamports, quoted_tokens: u64 },
    Sell { tokens: TokenAmount, quoted_sol: u64, exit_intent: String, urgent: bool },
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct PaperOrder {
    pub order_id: String,
    pub intent_id: String,
    pub mint: String,
    pub position_id: String,
    pub side: PaperSide,
    pub max_slippage: Bps,
    pub target_slot: u64,
    pub deadline_ms: i64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum FillResult {
    Bought { tokens: TokenAmount, sol_cost: Lamports, curve_fees: Lamports, impact: Bps },
    Sold { sol_out: Lamports, curve_fees: Lamports, impact: Bps },
    Failed { reason: String },
}

impl PaperOrder {
    pub fn min_out(&self) -> u64 {
        match &self.side {
            PaperSide::Buy { quoted_tokens, .. } => min_out_with_slippage(*quoted_tokens, self.max_slippage),
            PaperSide::Sell { quoted_sol, .. } => min_out_with_slippage(*quoted_sol, self.max_slippage),
        }
    }

    /// Is the order ready to be priced?
    pub fn ready(&self, observed_slot: u64, now_ms: i64) -> bool {
        observed_slot >= self.target_slot || now_ms >= self.deadline_ms
    }

    pub fn fill(&self, t: &TokenState) -> FillResult {
        if t.complete {
            return FillResult::Failed { reason: "curve complete at landing".into() };
        }
        match &self.side {
            PaperSide::Buy { budget, .. } => match t.curve.quote_buy(*budget, t.fees) {
                Ok(q) if q.tokens_out.0 >= self.min_out() => FillResult::Bought { tokens: q.tokens_out, sol_cost: q.sol_cost, curve_fees: q.fee, impact: q.price_impact },
                Ok(q) => FillResult::Failed { reason: format!("slippage: {} tokens < min {}", q.tokens_out.0, self.min_out()) },
                Err(e) => FillResult::Failed { reason: e.to_string() },
            },
            PaperSide::Sell { tokens, .. } => match t.curve.quote_sell(*tokens, t.fees) {
                Ok(q) if q.sol_out.0 >= self.min_out() => FillResult::Sold { sol_out: q.sol_out, curve_fees: q.fee, impact: q.price_impact },
                Ok(q) => FillResult::Failed { reason: format!("slippage: {} lamports < min {}", q.sol_out.0, self.min_out()) },
                Err(e) => FillResult::Failed { reason: e.to_string() },
            },
        }
    }
}
