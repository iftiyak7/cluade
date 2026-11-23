//! Persistence effects emitted by the engine. The engine never does I/O;
//! the driver hands these to `pf-db` (or drops them when no DB is set).

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub enum DbOp {
    Token {
        mint: String,
        symbol: String,
        name: String,
        uri: String,
        creator: String,
        bonding_curve: String,
        created_slot: u64,
        created_ms: i64,
        quote_mint: String,
        token_program: Option<String>,
        mayhem: bool,
        discovery_latency_us: i64,
    },
    Trade {
        signature: String,
        mint: String,
        trader: String,
        is_buy: bool,
        quote_amount: u64,
        token_amount: u64,
        virtual_quote_reserves: u64,
        virtual_token_reserves: u64,
        slot: u64,
        block_time: Option<i64>,
        quote_mint: String,
        fees: u64,
        ix_name: String,
    },
    CurveComplete { mint: String, at_ms: i64 },
    Score {
        mint: String,
        safety: u8,
        momentum: u8,
        liquidity: u8,
        opportunity: u8,
        hard_rejects: Vec<String>,
        factors: serde_json::Value,
    },
    Intent { intent_id: String, mint: String, strategy: String, buy_mode: String, size: u64, max_slippage_bps: u32, created_ms: i64, expires_ms: i64 },
    RiskDecision { intent_id: String, side: String, approved: bool, size: Option<u64>, reasons: Vec<String> },
    Order { order_id: String, intent_id: String, side: String, mint: String, position_id: Option<String>, requested_lamports: Option<u64>, requested_tokens: Option<u64>, min_out: u64, status: String, failure_reason: Option<String> },
    PaperFill { order_id: String, slot: u64, at_ms: i64, sol_delta: i64, token_delta: i64, curve_fees: u64, network_fee: u64, price_impact_bps: u32 },
    Position { position_id: String, mint: String, state: String, strategy: String, entry_intent_id: String, cost: u64, tokens_held: u64, tokens_bought: u64, proceeds: u64, peak_value: u64, tp_levels_hit: u16, opened_ms: Option<i64>, closed_ms: Option<i64>, exit_reason: Option<String>, state_json: serde_json::Value },
    PositionEvent { position_id: String, from: String, to: String, reason: String, at_ms: i64 },
    /// Full engine snapshot for restart recovery.
    Snapshot(serde_json::Value),
}
