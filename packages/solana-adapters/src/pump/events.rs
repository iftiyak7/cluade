//! Pump.fun Anchor events.
//!
//! Layouts follow the on-chain IDL snapshot in
//! `docs/api/pump/idl-onchain-2026-11-21.json` and are verified against real
//! mainnet transactions in `tests/fixtures`. Each event has a required
//! *core* and an optional *tail*; any bytes beyond what we understand are
//! counted in `unknown_trailing_bytes` (TradeEvent carried 16 such bytes on
//! 2026-11-21 — the deployed program is newer than its published IDL).

use crate::borsh::{Eof, Pubkey, Reader};

pub const DISC_TRADE: [u8; 8] = [189, 219, 127, 211, 78, 230, 97, 238];
pub const DISC_CREATE: [u8; 8] = [27, 114, 169, 77, 222, 235, 99, 118];
pub const DISC_COMPLETE: [u8; 8] = [95, 114, 97, 156, 212, 46, 152, 8];
pub const DISC_COMPLETE_AMM_MIGRATION: [u8; 8] = [189, 233, 93, 185, 92, 148, 234, 148];
/// Prefix of `emit_cpi!` self-invocation data (Anchor `EVENT_IX_TAG`, LE u64).
pub const EMIT_CPI_TAG: [u8; 8] = [0xe4, 0x45, 0xa5, 0x2e, 0x51, 0xcb, 0x9a, 0x1d];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Shareholder {
    pub address: Pubkey,
    pub share_bps: u16,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TradeTail {
    pub mayhem_mode: bool,
    pub cashback_fee_basis_points: u64,
    pub cashback: u64,
    pub buyback_fee_basis_points: u64,
    pub buyback_fee: u64,
    pub shareholders: Vec<Shareholder>,
    pub quote_mint: Pubkey,
    pub quote_amount: u64,
    pub virtual_quote_reserves: u64,
    pub real_quote_reserves: u64,
    pub holder_rewards_bps: u64,
    pub holder_rewards: u64,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TradeEvent {
    pub mint: Pubkey,
    /// Quote amount that moved into/out of the curve (excl. fees). For
    /// non-SOL quote curves this is in *quote* units, not lamports.
    pub sol_amount: u64,
    pub token_amount: u64,
    pub is_buy: bool,
    pub user: Pubkey,
    pub timestamp: i64,
    /// Post-trade reserves.
    pub virtual_sol_reserves: u64,
    pub virtual_token_reserves: u64,
    pub real_sol_reserves: u64,
    pub real_token_reserves: u64,
    pub fee_recipient: Pubkey,
    pub fee_basis_points: u64,
    pub fee: u64,
    pub creator: Pubkey,
    pub creator_fee_basis_points: u64,
    pub creator_fee: u64,
    pub track_volume: bool,
    pub total_unclaimed_tokens: u64,
    pub total_claimed_tokens: u64,
    pub current_sol_volume: u64,
    pub last_update_timestamp: i64,
    pub ix_name: String,
    pub tail: Option<TradeTail>,
    pub unknown_trailing_bytes: usize,
}

impl TradeEvent {
    pub fn quote_mint(&self) -> Pubkey {
        self.tail.as_ref().map(|t| t.quote_mint).unwrap_or(Pubkey::DEFAULT)
    }
    /// True when the curve is quoted in native SOL (amounts are lamports).
    pub fn is_sol_quoted(&self) -> bool {
        self.quote_mint() == Pubkey::DEFAULT
    }
    pub fn is_mayhem(&self) -> bool {
        self.tail.as_ref().is_some_and(|t| t.mayhem_mode)
    }
    /// Quote amount moved through the curve. Use this, not `sol_amount`:
    /// on non-SOL quote curves `sol_amount` and the `*_sol_reserves` fields
    /// are zero and the real values live in the tail.
    pub fn quote_amount(&self) -> u64 {
        self.tail.as_ref().map(|t| t.quote_amount).unwrap_or(self.sol_amount)
    }
    pub fn virtual_quote_reserves(&self) -> u64 {
        self.tail.as_ref().map(|t| t.virtual_quote_reserves).unwrap_or(self.virtual_sol_reserves)
    }
    pub fn real_quote_reserves(&self) -> u64 {
        self.tail.as_ref().map(|t| t.real_quote_reserves).unwrap_or(self.real_sol_reserves)
    }
    /// All fees charged to the trader on top of / out of the quote amount.
    pub fn trader_fees(&self) -> u64 {
        self.fee + self.creator_fee + self.tail.as_ref().map(|t| t.cashback).unwrap_or(0)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CreateTail {
    pub token_program: Pubkey,
    pub is_mayhem_mode: bool,
    pub is_cashback_enabled: bool,
    pub quote_mint: Pubkey,
    pub virtual_quote_reserves: u64,
    pub creator_fee_bps: u64,
    pub is_holder_reward: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CreateEvent {
    pub name: String,
    pub symbol: String,
    pub uri: String,
    pub mint: Pubkey,
    pub bonding_curve: Pubkey,
    pub user: Pubkey,
    pub creator: Pubkey,
    pub timestamp: i64,
    pub virtual_token_reserves: u64,
    /// For non-SOL quote curves this holds virtual *quote* reserves.
    pub virtual_sol_reserves: u64,
    pub real_token_reserves: u64,
    pub token_total_supply: u64,
    pub tail: Option<CreateTail>,
    pub unknown_trailing_bytes: usize,
}

impl CreateEvent {
    pub fn quote_mint(&self) -> Pubkey {
        self.tail.as_ref().map(|t| t.quote_mint).unwrap_or(Pubkey::DEFAULT)
    }
    pub fn is_sol_quoted(&self) -> bool {
        self.quote_mint() == Pubkey::DEFAULT
    }
    pub fn token_program(&self) -> Option<Pubkey> {
        self.tail.as_ref().map(|t| t.token_program)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CompleteEvent {
    pub user: Pubkey,
    pub mint: Pubkey,
    pub bonding_curve: Pubkey,
    pub timestamp: i64,
    pub quote_mint: Option<Pubkey>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CompleteAmmMigrationEvent {
    pub user: Pubkey,
    pub mint: Pubkey,
    pub mint_amount: u64,
    pub sol_amount: u64,
    pub pool_migration_fee: u64,
    pub bonding_curve: Pubkey,
    pub timestamp: i64,
    pub pool: Pubkey,
    pub quote_mint: Option<Pubkey>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PumpEvent {
    Trade(TradeEvent),
    Create(CreateEvent),
    Complete(CompleteEvent),
    CompleteAmmMigration(CompleteAmmMigrationEvent),
    /// A Pump.fun event we recognise structurally but do not decode.
    Other { discriminator: [u8; 8] },
}

impl PumpEvent {
    pub fn name(&self) -> &'static str {
        match self {
            PumpEvent::Trade(_) => "TradeEvent",
            PumpEvent::Create(_) => "CreateEvent",
            PumpEvent::Complete(_) => "CompleteEvent",
            PumpEvent::CompleteAmmMigration(_) => "CompletePumpAmmMigrationEvent",
            PumpEvent::Other { .. } => "Other",
        }
    }
    pub fn mint(&self) -> Option<Pubkey> {
        match self {
            PumpEvent::Trade(e) => Some(e.mint),
            PumpEvent::Create(e) => Some(e.mint),
            PumpEvent::Complete(e) => Some(e.mint),
            PumpEvent::CompleteAmmMigration(e) => Some(e.mint),
            PumpEvent::Other { .. } => None,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum DecodeError {
    #[error("data shorter than discriminator")]
    TooShort,
    #[error("truncated {event}: {source}")]
    Truncated { event: &'static str, source: Eof },
}

fn trade(r: &mut Reader) -> Result<TradeEvent, Eof> {
    let mut e = TradeEvent {
        mint: r.pubkey()?,
        sol_amount: r.u64()?,
        token_amount: r.u64()?,
        is_buy: r.bool()?,
        user: r.pubkey()?,
        timestamp: r.i64()?,
        virtual_sol_reserves: r.u64()?,
        virtual_token_reserves: r.u64()?,
        real_sol_reserves: r.u64()?,
        real_token_reserves: r.u64()?,
        fee_recipient: r.pubkey()?,
        fee_basis_points: r.u64()?,
        fee: r.u64()?,
        creator: r.pubkey()?,
        creator_fee_basis_points: r.u64()?,
        creator_fee: r.u64()?,
        track_volume: r.bool()?,
        total_unclaimed_tokens: r.u64()?,
        total_claimed_tokens: r.u64()?,
        current_sol_volume: r.u64()?,
        last_update_timestamp: r.i64()?,
        ix_name: r.string()?,
        tail: None,
        unknown_trailing_bytes: 0,
    };
    e.tail = r.optional(|r| {
        Ok(TradeTail {
            mayhem_mode: r.bool()?,
            cashback_fee_basis_points: r.u64()?,
            cashback: r.u64()?,
            buyback_fee_basis_points: r.u64()?,
            buyback_fee: r.u64()?,
            shareholders: r.vec(|r| Ok(Shareholder { address: r.pubkey()?, share_bps: r.u16()? }))?,
            quote_mint: r.pubkey()?,
            quote_amount: r.u64()?,
            virtual_quote_reserves: r.u64()?,
            real_quote_reserves: r.u64()?,
            holder_rewards_bps: r.u64()?,
            holder_rewards: r.u64()?,
        })
    });
    e.unknown_trailing_bytes = r.remaining();
    Ok(e)
}

fn create(r: &mut Reader) -> Result<CreateEvent, Eof> {
    let mut e = CreateEvent {
        name: r.string()?,
        symbol: r.string()?,
        uri: r.string()?,
        mint: r.pubkey()?,
        bonding_curve: r.pubkey()?,
        user: r.pubkey()?,
        creator: r.pubkey()?,
        timestamp: r.i64()?,
        virtual_token_reserves: r.u64()?,
        virtual_sol_reserves: r.u64()?,
        real_token_reserves: r.u64()?,
        token_total_supply: r.u64()?,
        tail: None,
        unknown_trailing_bytes: 0,
    };
    e.tail = r.optional(|r| {
        Ok(CreateTail {
            token_program: r.pubkey()?,
            is_mayhem_mode: r.bool()?,
            is_cashback_enabled: r.bool()?,
            quote_mint: r.pubkey()?,
            virtual_quote_reserves: r.u64()?,
            creator_fee_bps: r.u64()?,
            is_holder_reward: r.bool()?,
        })
    });
    e.unknown_trailing_bytes = r.remaining();
    Ok(e)
}

fn complete(r: &mut Reader) -> Result<CompleteEvent, Eof> {
    Ok(CompleteEvent {
        user: r.pubkey()?,
        mint: r.pubkey()?,
        bonding_curve: r.pubkey()?,
        timestamp: r.i64()?,
        quote_mint: r.optional(|r| r.pubkey()),
    })
}

fn complete_amm(r: &mut Reader) -> Result<CompleteAmmMigrationEvent, Eof> {
    Ok(CompleteAmmMigrationEvent {
        user: r.pubkey()?,
        mint: r.pubkey()?,
        mint_amount: r.u64()?,
        sol_amount: r.u64()?,
        pool_migration_fee: r.u64()?,
        bonding_curve: r.pubkey()?,
        timestamp: r.i64()?,
        pool: r.pubkey()?,
        quote_mint: r.optional(|r| r.pubkey()),
    })
}

/// Decode `discriminator || borsh(event)`. Returns `Ok(None)` for data that
/// is not a Pump.fun event discriminator we know at all.
pub fn decode_event(data: &[u8]) -> Result<Option<PumpEvent>, DecodeError> {
    if data.len() < 8 {
        return Err(DecodeError::TooShort);
    }
    let disc: [u8; 8] = data[..8].try_into().unwrap();
    let mut r = Reader::new(&data[8..]);
    let wrap = |event: &'static str| move |source: Eof| DecodeError::Truncated { event, source };
    let ev = match disc {
        DISC_TRADE => PumpEvent::Trade(trade(&mut r).map_err(wrap("TradeEvent"))?),
        DISC_CREATE => PumpEvent::Create(create(&mut r).map_err(wrap("CreateEvent"))?),
        DISC_COMPLETE => PumpEvent::Complete(complete(&mut r).map_err(wrap("CompleteEvent"))?),
        DISC_COMPLETE_AMM_MIGRATION => PumpEvent::CompleteAmmMigration(complete_amm(&mut r).map_err(wrap("CompletePumpAmmMigrationEvent"))?),
        _ if KNOWN_OTHER.contains(&disc) => PumpEvent::Other { discriminator: disc },
        _ => return Ok(None),
    };
    Ok(Some(ev))
}

/// Discriminators of other Pump.fun events seen on mainnet that we skip.
const KNOWN_OTHER: [[u8; 8]; 2] = [
    [146, 159, 189, 172, 146, 88, 56, 244], // CloseUserVolumeAccumulatorEvent
    [227, 190, 215, 206, 176, 180, 165, 132], // DistributeFeeToHoldersEvent (from IDL)
];

#[cfg(test)]
mod tests {
    use super::*;
    use sha2::{Digest, Sha256};

    fn anchor_disc(prefix: &str, name: &str) -> [u8; 8] {
        Sha256::digest(format!("{prefix}:{name}"))[..8].try_into().unwrap()
    }

    #[test]
    fn discriminators_match_anchor_derivation() {
        assert_eq!(DISC_TRADE, anchor_disc("event", "TradeEvent"));
        assert_eq!(DISC_CREATE, anchor_disc("event", "CreateEvent"));
        assert_eq!(DISC_COMPLETE, anchor_disc("event", "CompleteEvent"));
        assert_eq!(DISC_COMPLETE_AMM_MIGRATION, anchor_disc("event", "CompletePumpAmmMigrationEvent"));
        assert_eq!(KNOWN_OTHER[0], anchor_disc("event", "CloseUserVolumeAccumulatorEvent"));
        assert_eq!(KNOWN_OTHER[1], anchor_disc("event", "DistributeFeeToHoldersEvent"));
        let mut tag: [u8; 8] = Sha256::digest("anchor:event")[..8].try_into().unwrap();
        tag.reverse();
        assert_eq!(EMIT_CPI_TAG, tag);
    }

    /// SYNTHETIC: no CompleteEvent was observed in the sampled mainnet
    /// window; this checks the decoder against the IDL layout only.
    #[test]
    fn complete_event_synthetic_layout() {
        let mut d = DISC_COMPLETE.to_vec();
        d.extend([1u8; 32]);
        d.extend([2u8; 32]);
        d.extend([3u8; 32]);
        d.extend(42i64.to_le_bytes());
        match decode_event(&d).unwrap().unwrap() {
            PumpEvent::Complete(e) => {
                assert_eq!(e.mint, Pubkey([2; 32]));
                assert_eq!(e.timestamp, 42);
                assert_eq!(e.quote_mint, None);
            }
            other => panic!("{other:?}"),
        }
        d.extend([0u8; 32]);
        assert!(matches!(decode_event(&d).unwrap().unwrap(), PumpEvent::Complete(CompleteEvent { quote_mint: Some(Pubkey::DEFAULT), .. })));
    }

    #[test]
    fn unknown_and_truncated() {
        assert_eq!(decode_event(&[9u8; 16]).unwrap(), None);
        let mut d = DISC_TRADE.to_vec();
        d.extend([0u8; 40]);
        assert!(matches!(decode_event(&d), Err(DecodeError::Truncated { event: "TradeEvent", .. })));
    }
}
