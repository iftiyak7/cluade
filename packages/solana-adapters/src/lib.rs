//! Solana / Pump.fun adapters.
//!
//! Currently contains only the pure bonding-curve math (`curve`). RPC,
//! Geyser and instruction builders will be added in Buildings 03 and 05.

pub mod curve;

/// Pump.fun bonding-curve program id (mainnet). Verify against the current
/// official IDL before live use — Pump.fun has upgraded its program and fee
/// structure several times.
pub const PUMP_FUN_PROGRAM_ID: &str = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
