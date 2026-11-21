//! Decoded transactions → deduplicated, versioned events.

use crate::stats::Stats;
use pf_events::{Envelope, Event, TokenDiscovered, TradeObserved};
use pf_solana::pump::events::PumpEvent;
use pf_solana::pump::tx::TxEvents;
use pf_types::{Side, TokenAmount};
use std::collections::{HashSet, VecDeque};
use std::time::{Instant, SystemTime, UNIX_EPOCH};

pub const PRODUCER: &str = "token-discovery";

/// Bounded "seen" set: WS + block sources and reconnect replays overlap.
pub struct Dedupe {
    set: HashSet<String>,
    order: VecDeque<String>,
    cap: usize,
}

impl Dedupe {
    pub fn new(cap: usize) -> Self {
        Self { set: HashSet::with_capacity(cap), order: VecDeque::with_capacity(cap), cap }
    }
    /// Returns true the first time a key is seen.
    pub fn insert(&mut self, k: &str) -> bool {
        if self.set.contains(k) {
            return false;
        }
        if self.order.len() == self.cap {
            if let Some(old) = self.order.pop_front() {
                self.set.remove(&old);
            }
        }
        self.set.insert(k.to_owned());
        self.order.push_back(k.to_owned());
        true
    }
}

pub fn now_us() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_micros() as i64).unwrap_or(0)
}

pub struct Pipeline {
    seen: Dedupe,
    pub stats: Stats,
    pub tip_slot: u64,
}

/// One transaction as received from a source.
pub struct Received {
    pub tx: TxEvents,
    pub at: Instant,
    pub at_wall_us: i64,
}

impl Pipeline {
    pub fn new() -> Self {
        Self { seen: Dedupe::new(500_000), stats: Stats::default(), tip_slot: 0 }
    }

    pub fn handle(&mut self, r: Received) -> Vec<Envelope> {
        let tx = r.tx;
        self.stats.inc("tx_received");
        if tx.failed {
            self.stats.inc("tx_failed_skipped");
            return vec![];
        }
        if !self.seen.insert(&tx.signature) {
            self.stats.inc("tx_duplicate");
            return vec![];
        }
        if tx.truncated_logs {
            self.stats.inc("tx_truncated_logs");
        }
        self.stats.add("decode_errors", tx.decode_errors.len() as u64);
        let mut out = Vec::new();
        for ev in tx.events {
            let event = match ev {
                PumpEvent::Create(c) => {
                    self.stats.inc("create");
                    if !c.is_sol_quoted() {
                        self.stats.inc("create_non_sol_quote");
                    }
                    let tail = c.tail.as_ref();
                    Event::TokenDiscovered(TokenDiscovered {
                        quote_mint: c.quote_mint().to_string(),
                        token_program: c.token_program().map(|p| p.to_string()),
                        mayhem_mode: tail.is_some_and(|t| t.is_mayhem_mode),
                        creator_fee_bps: tail.map(|t| t.creator_fee_bps),
                        mint: c.mint.to_string(),
                        bonding_curve: c.bonding_curve.to_string(),
                        creator: c.creator.to_string(),
                        name: c.name,
                        symbol: c.symbol,
                        uri: c.uri,
                        slot: tx.slot,
                        signature: tx.signature.clone(),
                        block_time: tx.block_time,
                        received_at_us: r.at_wall_us,
                        decoded_at_us: now_us(),
                    })
                }
                PumpEvent::Trade(t) => {
                    self.stats.inc(if t.is_buy { "trade_buy" } else { "trade_sell" });
                    Event::TradeObserved(TradeObserved {
                        mint: t.mint.to_string(),
                        signature: tx.signature.clone(),
                        trader: t.user.to_string(),
                        side: if t.is_buy { Side::Buy } else { Side::Sell },
                        quote_amount: t.quote_amount(),
                        token_amount: TokenAmount(t.token_amount),
                        virtual_quote_reserves: t.virtual_quote_reserves(),
                        virtual_token_reserves: t.virtual_token_reserves,
                        real_quote_reserves: t.real_quote_reserves(),
                        real_token_reserves: t.real_token_reserves,
                        quote_mint: t.quote_mint().to_string(),
                        fee_bps: t.fee_basis_points,
                        creator_fee_bps: t.creator_fee_basis_points,
                        fees: t.trader_fees(),
                        ix_name: t.ix_name.clone(),
                        slot: tx.slot,
                        block_time: tx.block_time,
                    })
                }
                PumpEvent::Complete(c) => {
                    self.stats.inc("complete");
                    Event::CurveCompleted { mint: c.mint.to_string(), slot: tx.slot, signature: tx.signature.clone(), migrated_pool: None }
                }
                PumpEvent::CompleteAmmMigration(m) => {
                    self.stats.inc("migration");
                    Event::CurveCompleted { mint: m.mint.to_string(), slot: tx.slot, signature: tx.signature.clone(), migrated_pool: Some(m.pool.to_string()) }
                }
                PumpEvent::Other { .. } => continue,
            };
            out.push(Envelope::new(PRODUCER, now_us() / 1000, None, event));
        }
        self.stats.decode_us.record(r.at.elapsed().as_micros() as u64);
        if let Some(bt) = tx.block_time {
            self.stats.block_lag_ms.record((now_us() / 1000 - bt * 1000).max(0) as u64);
        }
        if self.tip_slot >= tx.slot {
            self.stats.slot_lag.record(self.tip_slot - tx.slot);
        }
        out
    }
}

impl Default for Pipeline {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pf_solana::pump::tx::events_from_transaction;

    fn fixture(name: &str) -> serde_json::Value {
        let p = format!("{}/../../packages/solana-adapters/tests/fixtures/{name}.json", env!("CARGO_MANIFEST_DIR"));
        serde_json::from_str(&std::fs::read_to_string(p).unwrap()).unwrap()
    }

    fn recv(name: &str) -> Received {
        Received { tx: events_from_transaction(&fixture(name)).unwrap(), at: Instant::now(), at_wall_us: now_us() }
    }

    #[test]
    fn create_tx_emits_discovery_and_trade_and_dedupes() {
        let mut p = Pipeline::new();
        let out = p.handle(recv("create_sol"));
        assert_eq!(out.len(), 2);
        match &out[0].event {
            Event::TokenDiscovered(d) => {
                assert_eq!(d.quote_mint, "11111111111111111111111111111111");
                assert!(d.decoded_at_us >= d.received_at_us);
            }
            e => panic!("{e:?}"),
        }
        assert!(matches!(out[1].event, Event::TradeObserved(_)));
        assert!(p.handle(recv("create_sol")).is_empty(), "duplicate signature suppressed");
        assert_eq!(p.stats.counters["tx_duplicate"], 1);
    }

    #[test]
    fn non_sol_quote_is_flagged() {
        let mut p = Pipeline::new();
        let out = p.handle(recv("create_quote_mint"));
        let Event::TokenDiscovered(d) = &out[0].event else { panic!() };
        assert_ne!(d.quote_mint, "11111111111111111111111111111111");
        assert_eq!(p.stats.counters["create_non_sol_quote"], 1);
        let Event::TradeObserved(t) = &out[1].event else { panic!() };
        assert!(t.quote_amount > 0, "quote-denominated amount, not the zero sol field");
    }

    #[test]
    fn dedupe_is_bounded() {
        let mut d = Dedupe::new(2);
        assert!(d.insert("a") && d.insert("b") && d.insert("c"));
        assert!(d.insert("a"), "evicted entries may reappear");
        assert!(!d.insert("c"));
    }
}
