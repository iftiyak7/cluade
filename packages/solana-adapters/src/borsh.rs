//! Minimal, allocation-light Borsh reader for Anchor event/account data.
//!
//! Hand-rolled on purpose: the decoder sits on the discovery hot path and
//! must tolerate *forward-compatible* layouts (Pump.fun appends fields to
//! events; the on-chain IDL lags the deployed program). Callers decode a
//! required core, then optional tails, and record unknown trailing bytes.

use std::fmt;

#[derive(Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Default)]
pub struct Pubkey(pub [u8; 32]);

impl Pubkey {
    /// The all-zero key (System Program); Pump.fun uses it for "SOL" as quote.
    pub const DEFAULT: Pubkey = Pubkey([0; 32]);

    pub fn from_base58(s: &str) -> Option<Pubkey> {
        let v = bs58::decode(s).into_vec().ok()?;
        Some(Pubkey(v.try_into().ok()?))
    }
    pub fn to_base58(&self) -> String {
        bs58::encode(self.0).into_string()
    }
}

impl fmt::Display for Pubkey {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.to_base58())
    }
}
impl fmt::Debug for Pubkey {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Pubkey({})", self.to_base58())
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, thiserror::Error)]
#[error("unexpected end of data at offset {0}")]
pub struct Eof(pub usize);

pub struct Reader<'a> {
    buf: &'a [u8],
    pos: usize,
}

impl<'a> Reader<'a> {
    pub fn new(buf: &'a [u8]) -> Self {
        Self { buf, pos: 0 }
    }
    pub fn remaining(&self) -> usize {
        self.buf.len() - self.pos
    }
    pub fn position(&self) -> usize {
        self.pos
    }
    pub fn take(&mut self, n: usize) -> Result<&'a [u8], Eof> {
        if self.remaining() < n {
            return Err(Eof(self.pos));
        }
        let s = &self.buf[self.pos..self.pos + n];
        self.pos += n;
        Ok(s)
    }
    pub fn u8(&mut self) -> Result<u8, Eof> {
        Ok(self.take(1)?[0])
    }
    pub fn bool(&mut self) -> Result<bool, Eof> {
        Ok(self.u8()? != 0)
    }
    pub fn u16(&mut self) -> Result<u16, Eof> {
        Ok(u16::from_le_bytes(self.take(2)?.try_into().unwrap()))
    }
    pub fn u32(&mut self) -> Result<u32, Eof> {
        Ok(u32::from_le_bytes(self.take(4)?.try_into().unwrap()))
    }
    pub fn u64(&mut self) -> Result<u64, Eof> {
        Ok(u64::from_le_bytes(self.take(8)?.try_into().unwrap()))
    }
    pub fn i64(&mut self) -> Result<i64, Eof> {
        Ok(i64::from_le_bytes(self.take(8)?.try_into().unwrap()))
    }
    pub fn pubkey(&mut self) -> Result<Pubkey, Eof> {
        Ok(Pubkey(self.take(32)?.try_into().unwrap()))
    }
    /// Borsh string. Lengths are bounded so a corrupt prefix cannot make us
    /// allocate gigabytes.
    pub fn string(&mut self) -> Result<String, Eof> {
        let n = self.u32()? as usize;
        let b = self.take(n)?;
        Ok(String::from_utf8_lossy(b).into_owned())
    }
    pub fn vec<T>(&mut self, mut f: impl FnMut(&mut Self) -> Result<T, Eof>) -> Result<Vec<T>, Eof> {
        let n = self.u32()? as usize;
        if n > self.remaining() {
            return Err(Eof(self.pos));
        }
        (0..n).map(|_| f(self)).collect()
    }
    /// Run `f`; on EOF rewind and return `None` (all-or-nothing optional tail).
    pub fn optional<T>(&mut self, f: impl FnOnce(&mut Self) -> Result<T, Eof>) -> Option<T> {
        let start = self.pos;
        match f(self) {
            Ok(v) => Some(v),
            Err(_) => {
                self.pos = start;
                None
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_and_rewinds() {
        let data = [1u8, 0, 0, 0, b'x', 7, 0, 0, 0, 0, 0, 0, 0];
        let mut r = Reader::new(&data);
        assert_eq!(r.string().unwrap(), "x");
        assert_eq!(r.u64().unwrap(), 7);
        assert!(r.optional(|r| r.u64()).is_none());
        assert_eq!(r.remaining(), 0);
    }

    #[test]
    fn bounded_vec_len() {
        let data = [0xff, 0xff, 0xff, 0x7f];
        assert!(Reader::new(&data).vec(|r| r.u8()).is_err());
    }

    #[test]
    fn pubkey_base58_roundtrip() {
        let s = "6EF8rrecthR5Dkzon8Nwu78hRvfCKubJ14M5uBEwF6P";
        assert_eq!(Pubkey::from_base58(s).unwrap().to_string(), s);
        assert_eq!(Pubkey::DEFAULT.to_string(), "11111111111111111111111111111111");
    }
}
