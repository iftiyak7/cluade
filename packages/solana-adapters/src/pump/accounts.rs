//! Pump.fun account decoders (Global config, BondingCurve).

use crate::borsh::{Eof, Pubkey, Reader};

pub const DISC_GLOBAL: [u8; 8] = [167, 232, 232, 177, 200, 108, 114, 127];
pub const DISC_BONDING_CURVE: [u8; 8] = [23, 183, 248, 55, 96, 216, 172, 96];

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum AccountError {
    #[error("wrong discriminator")]
    WrongDiscriminator,
    #[error(transparent)]
    Eof(#[from] Eof),
}

/// Subset of `Global` needed for pricing new curves.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Global {
    pub initialized: bool,
    pub authority: Pubkey,
    pub fee_recipient: Pubkey,
    pub initial_virtual_token_reserves: u64,
    pub initial_virtual_sol_reserves: u64,
    pub initial_real_token_reserves: u64,
    pub token_total_supply: u64,
    pub fee_basis_points: u64,
    pub withdraw_authority: Pubkey,
    pub enable_migrate: bool,
    pub pool_migration_fee: u64,
    /// Default creator fee; individual curves may override (`creator_fee_bps`).
    pub creator_fee_basis_points: u64,
}

pub fn decode_global(data: &[u8]) -> Result<Global, AccountError> {
    if data.len() < 8 || data[..8] != DISC_GLOBAL {
        return Err(AccountError::WrongDiscriminator);
    }
    let r = &mut Reader::new(&data[8..]);
    Ok(Global {
        initialized: r.bool()?,
        authority: r.pubkey()?,
        fee_recipient: r.pubkey()?,
        initial_virtual_token_reserves: r.u64()?,
        initial_virtual_sol_reserves: r.u64()?,
        initial_real_token_reserves: r.u64()?,
        token_total_supply: r.u64()?,
        fee_basis_points: r.u64()?,
        withdraw_authority: r.pubkey()?,
        enable_migrate: r.bool()?,
        pool_migration_fee: r.u64()?,
        creator_fee_basis_points: r.u64()?,
    })
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BondingCurveTail {
    pub is_mayhem_mode: bool,
    pub is_cashback_coin: bool,
    pub quote_mint: Pubkey,
    pub creator_fee_bps: u64,
    pub can_edit_creator_fee: bool,
    pub is_holder_reward: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct BondingCurveAccount {
    pub virtual_token_reserves: u64,
    pub virtual_quote_reserves: u64,
    pub real_token_reserves: u64,
    pub real_quote_reserves: u64,
    pub token_total_supply: u64,
    pub complete: bool,
    pub creator: Option<Pubkey>,
    /// Older curves were allocated before these fields existed.
    pub tail: Option<BondingCurveTail>,
}

pub fn decode_bonding_curve(data: &[u8]) -> Result<BondingCurveAccount, AccountError> {
    if data.len() < 8 || data[..8] != DISC_BONDING_CURVE {
        return Err(AccountError::WrongDiscriminator);
    }
    let r = &mut Reader::new(&data[8..]);
    Ok(BondingCurveAccount {
        virtual_token_reserves: r.u64()?,
        virtual_quote_reserves: r.u64()?,
        real_token_reserves: r.u64()?,
        real_quote_reserves: r.u64()?,
        token_total_supply: r.u64()?,
        complete: r.bool()?,
        creator: r.optional(|r| r.pubkey()),
        tail: r.optional(|r| {
            Ok(BondingCurveTail {
                is_mayhem_mode: r.bool()?,
                is_cashback_coin: r.bool()?,
                quote_mint: r.pubkey()?,
                creator_fee_bps: r.u64()?,
                can_edit_creator_fee: r.bool()?,
                is_holder_reward: r.bool()?,
            })
        }),
    })
}
