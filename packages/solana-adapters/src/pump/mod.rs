//! Pump.fun bonding-curve program adapters.

pub mod accounts;
pub mod events;
pub mod tx;

/// Pump.fun bonding-curve program (mainnet). Verified on-chain 2026-11-21.
pub const PROGRAM_ID: &str = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
/// `Global` PDA (seeds: ["global"]).
pub const GLOBAL_ACCOUNT: &str = "4wTV1YmiEkRvAtNtsSGPtUrqRYQMe5SKy2uB4Jjaxnjf";
/// Token-2022 program — new Pump.fun mints (create_v2) use it, so mint
/// extensions must be inspected before buying.
pub const TOKEN_2022_PROGRAM: &str = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb";
pub const TOKEN_PROGRAM: &str = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA";
