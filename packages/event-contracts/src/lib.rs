//! Versioned inter-service event contracts.
//!
//! Every event travels inside an [`Envelope`]. Consumers MUST dedupe on
//! `event_id` (delivery is at-least-once) and MUST ignore events whose
//! `schema_version` major they do not understand.

use pf_types::{Address, Bps, Lamports, Side, TimestampMs, TokenAmount, TradingMode};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub const SCHEMA_VERSION: u16 = 1;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Envelope {
    pub event_id: Uuid,
    pub schema_version: u16,
    pub produced_at: TimestampMs,
    pub producer: String,
    /// Correlates everything that stems from one token / intent.
    pub correlation_id: Option<Uuid>,
    pub event: Event,
}

impl Envelope {
    pub fn new(producer: &str, produced_at: TimestampMs, correlation_id: Option<Uuid>, event: Event) -> Self {
        Self {
            event_id: Uuid::new_v4(),
            schema_version: SCHEMA_VERSION,
            produced_at,
            producer: producer.to_string(),
            correlation_id,
            event,
        }
    }

    /// Redis stream the event is published to.
    pub fn stream(&self) -> &'static str {
        self.event.stream()
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type", content = "data")]
pub enum Event {
    TokenDiscovered(TokenDiscovered),
    TradeObserved(TradeObserved),
    CurveCompleted { mint: Address, slot: u64, signature: String, migrated_pool: Option<Address> },
    MarketSnapshot(MarketSnapshot),
    TokenScored(TokenScored),
    EntryIntent(EntryIntent),
    ExitIntent(ExitIntent),
    RiskDecision(RiskDecision),
    CircuitBreakerTripped { breaker: String, reason: String, mode: TradingMode },
    OrderSubmitted { order_id: Uuid, intent_id: Uuid, signature: String, rpc: String },
    OrderFilled(OrderFilled),
    OrderFailed { order_id: Uuid, intent_id: Uuid, reason: String },
    PositionStateChanged { position_id: Uuid, mint: Address, from: String, to: String, reason: String },
    ConfigChanged { version: u32 },
    OperatorCommand(OperatorCommand),
    ServiceHealth { service: String, healthy: bool, detail: String },
}

impl Event {
    pub fn stream(&self) -> &'static str {
        match self {
            Event::TokenDiscovered(_) | Event::TradeObserved(_) | Event::CurveCompleted { .. } => "pf:events:discovery",
            Event::MarketSnapshot(_) => "pf:events:market",
            Event::TokenScored(_) => "pf:events:intel",
            Event::EntryIntent(_) | Event::ExitIntent(_) => "pf:events:intents",
            Event::RiskDecision(_) | Event::CircuitBreakerTripped { .. } => "pf:events:risk",
            Event::OrderSubmitted { .. } | Event::OrderFilled(_) | Event::OrderFailed { .. } => "pf:events:orders",
            Event::PositionStateChanged { .. } => "pf:events:positions",
            Event::ConfigChanged { .. } | Event::OperatorCommand(_) => "pf:events:control",
            Event::ServiceHealth { .. } => "pf:events:health",
        }
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TokenDiscovered {
    pub mint: Address,
    pub bonding_curve: Address,
    pub creator: Address,
    pub name: String,
    pub symbol: String,
    pub uri: String,
    pub slot: u64,
    pub signature: String,
    /// `11111111111111111111111111111111` = native SOL; anything else is a
    /// token-quoted curve (the bot holds SOL and should skip these).
    pub quote_mint: Address,
    /// Token program of the mint (Token-2022 mints need extension checks).
    pub token_program: Option<Address>,
    pub mayhem_mode: bool,
    pub creator_fee_bps: Option<u64>,
    pub block_time: Option<i64>,
    /// When the node saw the event vs. when we decoded it (latency metric).
    pub received_at_us: i64,
    pub decoded_at_us: i64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TradeObserved {
    pub mint: Address,
    pub signature: String,
    pub trader: Address,
    pub side: Side,
    /// Amount through the curve in *quote* units (lamports when SOL-quoted).
    pub quote_amount: u64,
    pub token_amount: TokenAmount,
    /// Post-trade reserves, quote-denominated.
    pub virtual_quote_reserves: u64,
    pub virtual_token_reserves: u64,
    pub real_quote_reserves: u64,
    pub real_token_reserves: u64,
    pub quote_mint: Address,
    pub fee_bps: u64,
    pub creator_fee_bps: u64,
    pub fees: u64,
    pub ix_name: String,
    pub slot: u64,
    pub block_time: Option<i64>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct MarketSnapshot {
    pub mint: Address,
    pub at: TimestampMs,
    pub virtual_sol_reserves: u64,
    pub virtual_token_reserves: u64,
    pub real_sol_reserves: u64,
    pub unique_buyers_1m: u32,
    pub unique_buyers_5m: u32,
    pub buy_volume_1m: Lamports,
    pub sell_volume_1m: Lamports,
    pub trades_1m: u32,
    pub holders: u32,
    pub top10_concentration: Bps,
    pub creator_holding: Bps,
    pub curve_complete: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TokenScored {
    pub mint: Address,
    pub safety: u8,
    pub momentum: u8,
    pub liquidity: u8,
    pub opportunity: u8,
    pub hard_rejects: Vec<String>,
    pub factors: Vec<ScoreFactor>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ScoreFactor {
    pub score: String,
    pub factor: String,
    pub contribution: i16,
    pub detail: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct EntryIntent {
    pub intent_id: Uuid,
    pub mint: Address,
    pub strategy: String,
    pub buy_mode: String,
    pub mode: TradingMode,
    pub size: Lamports,
    pub max_slippage: Bps,
    pub expires_at: TimestampMs,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ExitIntent {
    pub intent_id: Uuid,
    pub position_id: Uuid,
    pub mint: Address,
    pub tokens: TokenAmount,
    pub reason: String,
    pub urgent: bool,
    pub max_slippage: Bps,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct RiskDecision {
    pub intent_id: Uuid,
    pub side: Side,
    pub approved: bool,
    pub approved_size: Option<Lamports>,
    pub reasons: Vec<String>,
    /// Executors must not act on an approval after this time.
    pub valid_until: TimestampMs,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct OrderFilled {
    pub order_id: Uuid,
    pub intent_id: Uuid,
    pub side: Side,
    pub mint: Address,
    pub signature: String,
    /// Observed on-chain, not requested values.
    pub sol_amount: Lamports,
    pub token_amount: TokenAmount,
    pub fees: Lamports,
    pub slot: u64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "command", rename_all = "snake_case")]
pub enum OperatorCommand {
    StopNewBuys,
    PauseAllOrders,
    /// Requires reauth + second confirmation in admin-api.
    CloseAllPositions { confirmation_token: String },
    EnterSafeMode,
    Resume,
    PauseScanner,
    ResumeScanner,
    ResetBreaker { breaker: String },
    ApproveManualEntry { intent_id: Uuid },
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn envelope_roundtrip_and_tagging() {
        let env = Envelope::new(
            "risk-engine",
            1,
            None,
            Event::OperatorCommand(OperatorCommand::ResetBreaker { breaker: "daily_loss".into() }),
        );
        let json = serde_json::to_string(&env).unwrap();
        assert!(json.contains(r#""type":"OperatorCommand""#), "{json}");
        assert!(json.contains(r#""command":"reset_breaker""#), "{json}");
        let back: Envelope = serde_json::from_str(&json).unwrap();
        assert_eq!(back, env);
        assert_eq!(back.stream(), "pf:events:control");
    }
}
