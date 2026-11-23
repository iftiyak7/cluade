//! Solana / Pump.fun adapters.
//!
//! * [`curve`] — bonding-curve math, verified against mainnet trades.
//! * [`pump`] — event/account decoding and transaction parsing.
//! * [`borsh`] — forward-compatible Borsh reader.
//!
//! RPC/Geyser clients live in the services that use them.

pub mod borsh;
pub mod curve;
pub mod pump;
pub mod token;

pub use pump::PROGRAM_ID as PUMP_FUN_PROGRAM_ID;
