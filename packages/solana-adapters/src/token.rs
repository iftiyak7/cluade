//! SPL Token / Token-2022 mint inspection for the safety filter.
//!
//! Every Pump.fun launch observed on 2026-11-21 was a Token-2022 mint with
//! only `MetadataPointer` + `TokenMetadata` extensions, no mint authority and
//! no freeze authority. Anything else is suspicious and is reported so the
//! intelligence layer can hard-reject it. Unknown extension types fail
//! closed (treated as dangerous).

use crate::borsh::Pubkey;

pub const MINT_BASE_LEN: usize = 82;
/// Token-2022 pads the base mint to the token-account size, then stores
/// the account type, then TLV extensions.
const ACCOUNT_TYPE_OFFSET: usize = 165;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MintInfo {
    pub token_2022: bool,
    pub mint_authority: Option<Pubkey>,
    pub freeze_authority: Option<Pubkey>,
    pub supply: u64,
    pub decimals: u8,
    pub is_initialized: bool,
    pub extensions: Vec<u16>,
    /// Name from the TokenMetadata extension, if present.
    pub metadata_name: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error)]
pub enum MintError {
    #[error("account too short for a mint ({0} bytes)")]
    TooShort(usize),
    #[error("not a mint account (account type {0})")]
    NotMint(u8),
    #[error("malformed extension data")]
    BadTlv,
}

/// Token-2022 extension type ids (spl-token-2022 `ExtensionType`).
pub fn extension_name(t: u16) -> &'static str {
    match t {
        0 => "Uninitialized",
        1 => "TransferFeeConfig",
        2 => "TransferFeeAmount",
        3 => "MintCloseAuthority",
        4 => "ConfidentialTransferMint",
        5 => "ConfidentialTransferAccount",
        6 => "DefaultAccountState",
        7 => "ImmutableOwner",
        8 => "MemoTransfer",
        9 => "NonTransferable",
        10 => "InterestBearingConfig",
        11 => "CpiGuard",
        12 => "PermanentDelegate",
        13 => "NonTransferableAccount",
        14 => "TransferHook",
        15 => "TransferHookAccount",
        16 => "ConfidentialTransferFeeConfig",
        17 => "ConfidentialTransferFeeAmount",
        18 => "MetadataPointer",
        19 => "TokenMetadata",
        20 => "GroupPointer",
        21 => "TokenGroup",
        22 => "GroupMemberPointer",
        23 => "TokenGroupMember",
        24 => "ConfidentialMintBurn",
        25 => "ScaledUiAmount",
        26 => "Pausable",
        27 => "PausableAccount",
        _ => "Unknown",
    }
}

/// Extensions that are harmless for a trader.
const BENIGN: [u16; 5] = [18, 19, 20, 22, 23];

fn coption_pubkey(d: &[u8]) -> Option<Pubkey> {
    let tag = u32::from_le_bytes(d[0..4].try_into().unwrap());
    (tag == 1).then(|| Pubkey(d[4..36].try_into().unwrap()))
}

pub fn decode_mint(data: &[u8], owner_is_token_2022: bool) -> Result<MintInfo, MintError> {
    if data.len() < MINT_BASE_LEN {
        return Err(MintError::TooShort(data.len()));
    }
    let mut info = MintInfo {
        token_2022: owner_is_token_2022,
        mint_authority: coption_pubkey(&data[0..36]),
        supply: u64::from_le_bytes(data[36..44].try_into().unwrap()),
        decimals: data[44],
        is_initialized: data[45] != 0,
        freeze_authority: coption_pubkey(&data[46..82]),
        extensions: Vec::new(),
        metadata_name: None,
    };
    if data.len() > ACCOUNT_TYPE_OFFSET {
        let account_type = data[ACCOUNT_TYPE_OFFSET];
        if account_type != 1 {
            return Err(MintError::NotMint(account_type));
        }
        let mut o = ACCOUNT_TYPE_OFFSET + 1;
        while o + 4 <= data.len() {
            let t = u16::from_le_bytes([data[o], data[o + 1]]);
            let len = u16::from_le_bytes([data[o + 2], data[o + 3]]) as usize;
            if t == 0 {
                break;
            }
            let end = o + 4 + len;
            if end > data.len() {
                return Err(MintError::BadTlv);
            }
            if t == 19 {
                // TokenMetadata: update_authority(32) mint(32) name(string) ...
                let v = &data[o + 4..end];
                if v.len() >= 68 {
                    let n = u32::from_le_bytes(v[64..68].try_into().unwrap()) as usize;
                    if 68 + n <= v.len() {
                        info.metadata_name = Some(String::from_utf8_lossy(&v[68..68 + n]).into_owned());
                    }
                }
            }
            info.extensions.push(t);
            o = end;
        }
    }
    Ok(info)
}

impl MintInfo {
    /// Human-readable reasons this mint should not be bought. Empty = clean.
    pub fn red_flags(&self) -> Vec<String> {
        let mut r = Vec::new();
        if !self.is_initialized {
            r.push("mint not initialized".into());
        }
        if self.mint_authority.is_some() {
            r.push("mint authority present (supply can be inflated)".into());
        }
        if self.freeze_authority.is_some() {
            r.push("freeze authority present (holders can be frozen)".into());
        }
        for t in &self.extensions {
            if !BENIGN.contains(t) {
                r.push(format!("token extension {} ({t})", extension_name(*t)));
            }
        }
        r
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn base(mint_auth: bool, freeze: bool) -> Vec<u8> {
        let mut d = vec![0u8; MINT_BASE_LEN];
        if mint_auth {
            d[0] = 1;
            d[4..36].copy_from_slice(&[7; 32]);
        }
        d[36..44].copy_from_slice(&1_000u64.to_le_bytes());
        d[44] = 6;
        d[45] = 1;
        if freeze {
            d[46] = 1;
            d[50..82].copy_from_slice(&[8; 32]);
        }
        d
    }

    fn with_exts(mut d: Vec<u8>, exts: &[(u16, usize)]) -> Vec<u8> {
        d.resize(ACCOUNT_TYPE_OFFSET, 0);
        d.push(1);
        for (t, len) in exts {
            d.extend(t.to_le_bytes());
            d.extend((*len as u16).to_le_bytes());
            d.extend(vec![0u8; *len]);
        }
        d
    }

    // SYNTHETIC accounts: no mainnet Pump.fun mint with these properties was observed.
    #[test]
    fn legacy_clean_mint_has_no_flags() {
        let m = decode_mint(&base(false, false), false).unwrap();
        assert!(m.red_flags().is_empty());
        assert_eq!(m.decimals, 6);
    }

    #[test]
    fn authorities_are_flagged() {
        let m = decode_mint(&base(true, true), false).unwrap();
        assert_eq!(m.red_flags().len(), 2);
    }

    #[test]
    fn dangerous_and_unknown_extensions_are_flagged() {
        let d = with_exts(base(false, false), &[(18, 64), (14, 64), (12, 32), (999, 4)]);
        let m = decode_mint(&d, true).unwrap();
        let f = m.red_flags().join("; ");
        assert!(f.contains("TransferHook") && f.contains("PermanentDelegate") && f.contains("Unknown"), "{f}");
        assert!(!f.contains("MetadataPointer"));
    }

    #[test]
    fn truncated_tlv_is_error() {
        let mut d = with_exts(base(false, false), &[(18, 64)]);
        d.truncate(d.len() - 10);
        assert_eq!(decode_mint(&d, true), Err(MintError::BadTlv));
    }
}
