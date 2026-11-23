//! Per-token market state built from `TokenDiscovered` / `TradeObserved`.
//!
//! Only tokens whose creation we observed are tracked in full (new-launch
//! strategy, bounded memory). Holder balances are *net trade flow on the
//! curve*: wallet-to-wallet transfers are invisible here, so holder counts
//! and concentration are approximations, labelled as such in scores.
//!
//! Time is chain time (`block_time`, 1 s resolution) in milliseconds.

use pf_events::{TokenDiscovered, TradeObserved};
use pf_intel::{MarketStats, TokenFacts};
use pf_solana::curve::{CurveState, FeeSchedule, TOKEN_TOTAL_SUPPLY};
use pf_types::{Bps, Lamports, Side, TokenAmount};
use std::collections::{HashMap, HashSet, VecDeque};

pub const SOL_QUOTE: &str = "11111111111111111111111111111111";
const WINDOW_MS: i64 = 5 * 60_000;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TradeRec {
    pub ms: i64,
    pub slot: u64,
    pub trader: String,
    pub is_buy: bool,
    pub quote: u64,
    pub tokens: u64,
}

#[derive(Debug, Clone)]
pub struct TokenState {
    pub mint: String,
    pub creator: String,
    pub bonding_curve: String,
    pub name: String,
    pub symbol: String,
    pub quote_mint: String,
    pub token_program: Option<String>,
    pub mayhem: bool,
    pub created_slot: u64,
    pub created_ms: i64,
    pub curve: CurveState,
    pub fees: FeeSchedule,
    pub initial_price: u128,
    pub last_slot: u64,
    pub last_trade_ms: i64,
    pub n_trades: u64,
    pub complete: bool,
    pub migrated_pool: Option<String>,
    trades: VecDeque<TradeRec>,
    prices: VecDeque<(i64, u128)>,
    balances: HashMap<String, i128>,
    pub creator_sold: bool,
    pub non_creator_sell_seen: bool,
    pub same_slot_buy_tokens: u64,
}

impl TokenState {
    pub fn from_create(d: &TokenDiscovered) -> Self {
        let created_ms = d.block_time.map(|t| t * 1000).unwrap_or(d.received_at_us / 1000);
        let curve = CurveState::fresh();
        Self {
            mint: d.mint.clone(),
            creator: d.creator.clone(),
            bonding_curve: d.bonding_curve.clone(),
            name: d.name.clone(),
            symbol: d.symbol.clone(),
            quote_mint: d.quote_mint.clone(),
            token_program: d.token_program.clone(),
            mayhem: d.mayhem_mode,
            created_slot: d.slot,
            created_ms,
            initial_price: 0,
            curve,
            fees: FeeSchedule::new(95, d.creator_fee_bps.unwrap_or(5) as u32),
            last_slot: d.slot,
            last_trade_ms: created_ms,
            n_trades: 0,
            complete: false,
            migrated_pool: None,
            trades: VecDeque::new(),
            prices: VecDeque::new(),
            balances: HashMap::new(),
            creator_sold: false,
            non_creator_sell_seen: false,
            same_slot_buy_tokens: 0,
        }
    }

    pub fn is_sol_quoted(&self) -> bool {
        self.quote_mint == SOL_QUOTE
    }

    pub fn age_secs(&self, now_ms: i64) -> u64 {
        ((now_ms - self.created_ms).max(0) / 1000) as u64
    }

    pub fn apply_trade(&mut self, t: &TradeObserved) {
        let ms = t.block_time.map(|b| b * 1000).unwrap_or(self.last_trade_ms);
        self.curve = CurveState {
            virtual_sol_reserves: t.virtual_quote_reserves,
            virtual_token_reserves: t.virtual_token_reserves,
            real_sol_reserves: t.real_quote_reserves,
            real_token_reserves: t.real_token_reserves,
            complete: t.real_token_reserves == 0,
        };
        self.fees = FeeSchedule::new(t.fee_bps as u32, t.creator_fee_bps as u32);
        let price = self.curve.spot_price_scaled();
        if self.initial_price == 0 {
            // price just before the first observed trade
            let pre_vs = if t.side == Side::Buy { t.virtual_quote_reserves.saturating_sub(t.quote_amount) } else { t.virtual_quote_reserves + t.quote_amount };
            let pre_vt = if t.side == Side::Buy { t.virtual_token_reserves + t.token_amount.0 } else { t.virtual_token_reserves.saturating_sub(t.token_amount.0) };
            self.initial_price = CurveState { virtual_sol_reserves: pre_vs, virtual_token_reserves: pre_vt.max(1), real_sol_reserves: 0, real_token_reserves: 0, complete: false }.spot_price_scaled();
        }
        let is_buy = t.side == Side::Buy;
        let bal = self.balances.entry(t.trader.clone()).or_default();
        *bal += if is_buy { t.token_amount.0 as i128 } else { -(t.token_amount.0 as i128) };
        if !is_buy {
            if t.trader == self.creator {
                self.creator_sold = true;
            } else {
                self.non_creator_sell_seen = true;
            }
        }
        if is_buy && t.slot == self.created_slot && t.trader != self.creator {
            self.same_slot_buy_tokens += t.token_amount.0;
        }
        self.trades.push_back(TradeRec { ms, slot: t.slot, trader: t.trader.clone(), is_buy, quote: t.quote_amount, tokens: t.token_amount.0 });
        self.prices.push_back((ms, price));
        self.last_slot = self.last_slot.max(t.slot);
        self.last_trade_ms = self.last_trade_ms.max(ms);
        self.n_trades += 1;
        if self.curve.complete {
            self.complete = true;
        }
        while self.trades.front().is_some_and(|r| r.ms < ms - WINDOW_MS) {
            self.trades.pop_front();
        }
        while self.prices.len() > 1 && self.prices.front().is_some_and(|p| p.0 < ms - WINDOW_MS) {
            self.prices.pop_front();
        }
    }

    fn price_at_or_before(&self, ms: i64) -> u128 {
        let mut p = self.initial_price;
        for (t, px) in &self.prices {
            if *t <= ms {
                p = *px;
            } else {
                break;
            }
        }
        p
    }

    fn change_bps(&self, now_ms: i64, back_ms: i64) -> i64 {
        let then = self.price_at_or_before(now_ms - back_ms);
        let now = self.curve.spot_price_scaled();
        if then == 0 {
            0
        } else {
            ((now as i128 - then as i128) * 10_000 / then as i128) as i64
        }
    }

    pub fn holders(&self) -> u32 {
        self.balances.values().filter(|b| **b > 0).count() as u32
    }

    fn share_of_supply(v: i128) -> Bps {
        Bps(((v.max(0) as u128 * 10_000) / TOKEN_TOTAL_SUPPLY as u128) as u32)
    }

    pub fn top10_concentration(&self) -> Bps {
        let mut b: Vec<i128> = self.balances.values().copied().filter(|v| *v > 0).collect();
        b.sort_unstable_by(|a, b| b.cmp(a));
        Self::share_of_supply(b.iter().take(10).sum())
    }

    pub fn creator_holding(&self) -> Bps {
        Self::share_of_supply(self.balances.get(&self.creator).copied().unwrap_or(0))
    }

    pub fn trades_since(&self, ms: i64) -> impl Iterator<Item = &TradeRec> {
        self.trades.iter().filter(move |r| r.ms >= ms)
    }

    pub fn stats(&self, now_ms: i64, base_order: Lamports) -> MarketStats {
        let m1 = now_ms - 60_000;
        let mut buyers_1m = HashSet::new();
        let mut buyers_5m = HashSet::new();
        let (mut bv, mut sv, mut n1) = (0u64, 0u64, 0u32);
        let mut sides: HashMap<&str, (bool, bool, u64)> = HashMap::new();
        for r in self.trades_since(now_ms - WINDOW_MS) {
            if r.is_buy {
                buyers_5m.insert(r.trader.as_str());
            }
            if r.ms >= m1 {
                n1 += 1;
                if r.is_buy {
                    buyers_1m.insert(r.trader.as_str());
                    bv += r.quote;
                } else {
                    sv += r.quote;
                }
                let e = sides.entry(r.trader.as_str()).or_default();
                if r.is_buy { e.0 = true } else { e.1 = true }
                e.2 += r.quote;
            }
        }
        let total = bv + sv;
        let wash: u64 = sides.values().filter(|(b, s, _)| *b && *s).map(|(_, _, v)| *v).sum();
        let impact = self.curve.quote_buy(base_order, self.fees).map(|q| q.price_impact).unwrap_or(Bps(10_000));
        MarketStats {
            age_secs: self.age_secs(now_ms),
            unique_buyers_1m: buyers_1m.len() as u32,
            unique_buyers_5m: buyers_5m.len() as u32,
            buy_volume_1m: Lamports(bv),
            sell_volume_1m: Lamports(sv),
            trades_1m: n1,
            price_change_1m: self.change_bps(now_ms, 60_000),
            price_change_5m: self.change_bps(now_ms, 300_000),
            real_sol_reserves: Lamports(self.curve.real_sol_reserves),
            holders: self.holders(),
            wash_share: Bps(if total == 0 { 0 } else { (wash as u128 * 10_000 / total as u128) as u32 }),
            base_order_impact: impact,
        }
    }

    /// Facts observable from the trade stream. Mint/extension facts and
    /// creator history are filled in by the caller.
    pub fn facts(&self) -> TokenFacts {
        TokenFacts {
            top10_concentration: self.top10_concentration(),
            creator_holding: self.creator_holding(),
            same_slot_buy_share: Self::share_of_supply(self.same_slot_buy_tokens as i128),
            sell_observed: self.non_creator_sell_seen,
            creator_sold: self.creator_sold,
            ..Default::default()
        }
    }

    pub fn liquidation_value(&self, tokens: TokenAmount) -> Lamports {
        self.curve.liquidation_value(tokens, self.fees)
    }
}

/// Serializable essentials of a token, persisted for held positions so they
/// can be valued after a restart (rolling windows start empty again).
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub struct TokenSnapshot {
    pub mint: String,
    pub creator: String,
    pub bonding_curve: String,
    pub name: String,
    pub symbol: String,
    pub quote_mint: String,
    pub token_program: Option<String>,
    pub mayhem: bool,
    pub created_slot: u64,
    pub created_ms: i64,
    pub reserves: [u64; 4],
    pub complete: bool,
    pub fee_bps: [u32; 2],
    pub creator_sold: bool,
    pub last_slot: u64,
    pub last_trade_ms: i64,
}

impl TokenState {
    pub fn snapshot(&self) -> TokenSnapshot {
        let c = &self.curve;
        TokenSnapshot {
            mint: self.mint.clone(),
            creator: self.creator.clone(),
            bonding_curve: self.bonding_curve.clone(),
            name: self.name.clone(),
            symbol: self.symbol.clone(),
            quote_mint: self.quote_mint.clone(),
            token_program: self.token_program.clone(),
            mayhem: self.mayhem,
            created_slot: self.created_slot,
            created_ms: self.created_ms,
            reserves: [c.virtual_sol_reserves, c.virtual_token_reserves, c.real_sol_reserves, c.real_token_reserves],
            complete: self.complete,
            fee_bps: [self.fees.protocol_bps, self.fees.creator_bps],
            creator_sold: self.creator_sold,
            last_slot: self.last_slot,
            last_trade_ms: self.last_trade_ms,
        }
    }

    pub fn from_snapshot(s: &TokenSnapshot) -> Self {
        let curve = CurveState { virtual_sol_reserves: s.reserves[0], virtual_token_reserves: s.reserves[1], real_sol_reserves: s.reserves[2], real_token_reserves: s.reserves[3], complete: s.complete };
        Self {
            mint: s.mint.clone(),
            creator: s.creator.clone(),
            bonding_curve: s.bonding_curve.clone(),
            name: s.name.clone(),
            symbol: s.symbol.clone(),
            quote_mint: s.quote_mint.clone(),
            token_program: s.token_program.clone(),
            mayhem: s.mayhem,
            created_slot: s.created_slot,
            created_ms: s.created_ms,
            initial_price: curve.spot_price_scaled(),
            curve,
            fees: FeeSchedule::new(s.fee_bps[0], s.fee_bps[1]),
            last_slot: s.last_slot,
            last_trade_ms: s.last_trade_ms,
            n_trades: 0,
            complete: s.complete,
            migrated_pool: None,
            trades: VecDeque::new(),
            prices: VecDeque::new(),
            balances: HashMap::new(),
            creator_sold: s.creator_sold,
            non_creator_sell_seen: false,
            same_slot_buy_tokens: 0,
        }
    }
}

/// Per-creator launch history within this process (DB adds older history).
#[derive(Debug, Default, Clone)]
pub struct CreatorStats {
    pub launches: u32,
    /// Launches where the creator sold within 5 minutes (a rug proxy).
    pub quick_dumps: u32,
}

#[derive(Default)]
pub struct MarketBook {
    pub tokens: HashMap<String, TokenState>,
    pub creators: HashMap<String, CreatorStats>,
    pub untracked_trades: u64,
}

impl MarketBook {
    pub fn on_create(&mut self, d: &TokenDiscovered) -> &TokenState {
        self.creators.entry(d.creator.clone()).or_default().launches += 1;
        self.tokens.entry(d.mint.clone()).or_insert_with(|| TokenState::from_create(d))
    }

    pub fn restore(&mut self, s: &TokenSnapshot) {
        self.tokens.entry(s.mint.clone()).or_insert_with(|| TokenState::from_snapshot(s));
    }

    /// Returns the updated token, or None if we did not see its creation.
    pub fn on_trade(&mut self, t: &TradeObserved) -> Option<&TokenState> {
        let Some(tok) = self.tokens.get_mut(&t.mint) else {
            self.untracked_trades += 1;
            return None;
        };
        let was_sold = tok.creator_sold;
        tok.apply_trade(t);
        if !was_sold && tok.creator_sold && tok.last_trade_ms - tok.created_ms < 300_000 {
            self.creators.entry(tok.creator.clone()).or_default().quick_dumps += 1;
        }
        self.tokens.get(&t.mint)
    }

    pub fn on_complete(&mut self, mint: &str, pool: Option<String>) {
        if let Some(t) = self.tokens.get_mut(mint) {
            t.complete = true;
            t.curve.complete = true;
            t.migrated_pool = pool;
        }
    }

    /// Prior launches / quick dumps by this creator, excluding `mint` itself.
    pub fn creator_history(&self, creator: &str, mint: &str) -> (u32, u32) {
        let c = self.creators.get(creator).cloned().unwrap_or_default();
        let own_dump = self.tokens.get(mint).is_some_and(|t| t.creator_sold && t.last_trade_ms - t.created_ms < 300_000) as u32;
        (c.launches.saturating_sub(1), c.quick_dumps.saturating_sub(own_dump))
    }

    /// Drop tokens older than `max_age_ms` that `keep` does not protect.
    pub fn evict(&mut self, now_ms: i64, max_age_ms: i64, keep: impl Fn(&str) -> bool) -> usize {
        let before = self.tokens.len();
        self.tokens.retain(|m, t| keep(m) || now_ms - t.created_ms <= max_age_ms);
        before - self.tokens.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use pf_solana::curve::{INITIAL_VIRTUAL_SOL, INITIAL_VIRTUAL_TOKENS};

    pub fn create(mint: &str, creator: &str, bt: i64) -> TokenDiscovered {
        TokenDiscovered {
            mint: mint.into(),
            bonding_curve: "BC".into(),
            creator: creator.into(),
            name: "n".into(),
            symbol: "s".into(),
            uri: "u".into(),
            slot: 100,
            signature: "sig".into(),
            quote_mint: SOL_QUOTE.into(),
            token_program: None,
            mayhem_mode: false,
            creator_fee_bps: Some(30),
            block_time: Some(bt),
            received_at_us: bt * 1_000_000,
            decoded_at_us: bt * 1_000_000,
        }
    }

    /// Build a TradeObserved by actually applying the trade to a curve.
    pub fn trade(c: &mut CurveState, mint: &str, trader: &str, buy: bool, amount: u64, slot: u64, bt: i64) -> TradeObserved {
        let fees = FeeSchedule::new(95, 30);
        let (q, tok) = if buy {
            let r = c.apply_buy(Lamports(amount), fees).unwrap();
            (r.curve_input.0, r.tokens_out.0)
        } else {
            let r = c.apply_sell(TokenAmount(amount), fees).unwrap();
            (r.gross.0, amount)
        };
        TradeObserved {
            mint: mint.into(),
            signature: format!("{trader}{slot}{amount}"),
            trader: trader.into(),
            side: if buy { Side::Buy } else { Side::Sell },
            quote_amount: q,
            token_amount: TokenAmount(tok),
            virtual_quote_reserves: c.virtual_sol_reserves,
            virtual_token_reserves: c.virtual_token_reserves,
            real_quote_reserves: c.real_sol_reserves,
            real_token_reserves: c.real_token_reserves,
            quote_mint: SOL_QUOTE.into(),
            fee_bps: 95,
            creator_fee_bps: 30,
            fees: 0,
            ix_name: "buy_exact_quote_in".into(),
            slot,
            block_time: Some(bt),
        }
    }

    #[test]
    fn tracks_curve_holders_and_creator() {
        let mut book = MarketBook::default();
        book.on_create(&create("M", "CR", 1_000));
        let mut c = CurveState::fresh();
        book.on_trade(&trade(&mut c, "M", "CR", true, 1_000_000_000, 100, 1_000));
        book.on_trade(&trade(&mut c, "M", "A", true, 500_000_000, 100, 1_000));
        for i in 0..10 {
            book.on_trade(&trade(&mut c, "M", &format!("W{i}"), true, 200_000_000, 101 + i, 1_001 + i as i64));
        }
        let t = &book.tokens["M"];
        assert_eq!(t.curve.virtual_sol_reserves, c.virtual_sol_reserves);
        assert_eq!(t.holders(), 12);
        assert!(t.creator_holding().0 > 300, "creator bought ~1 SOL ≈ 3.4% of supply");
        assert!(t.facts().same_slot_buy_share.0 > 0, "A bought in the creation slot");
        let s = t.stats(1_011_000, Lamports::from_sol_milli(50));
        assert_eq!(s.unique_buyers_1m, 12);
        assert!(s.price_change_1m > 0);
        assert_eq!(s.wash_share, Bps(0));
        assert!(t.initial_price > 0 && t.initial_price <= CurveState::fresh().spot_price_scaled() + 1);
        let _ = (INITIAL_VIRTUAL_SOL, INITIAL_VIRTUAL_TOKENS);
    }

    #[test]
    fn creator_dump_is_recorded_in_history() {
        let mut book = MarketBook::default();
        book.on_create(&create("M1", "CR", 1_000));
        let mut c = CurveState::fresh();
        let b = trade(&mut c, "M1", "CR", true, 1_000_000_000, 100, 1_000);
        book.on_trade(&b);
        book.on_trade(&trade(&mut c, "M1", "CR", false, b.token_amount.0, 105, 1_030));
        book.on_create(&create("M2", "CR", 1_100));
        assert_eq!(book.creator_history("CR", "M2"), (1, 1));
        assert!(book.tokens["M1"].facts().creator_sold);
    }

    #[test]
    fn wash_trading_detected() {
        let mut book = MarketBook::default();
        book.on_create(&create("M", "CR", 1_000));
        let mut c = CurveState::fresh();
        for i in 0..5 {
            let b = trade(&mut c, "M", "WASH", true, 300_000_000, 101 + i, 1_001 + i as i64);
            book.on_trade(&b);
            book.on_trade(&trade(&mut c, "M", "WASH", false, b.token_amount.0, 101 + i, 1_001 + i as i64));
        }
        let s = book.tokens["M"].stats(1_010_000, Lamports::from_sol_milli(50));
        assert_eq!(s.wash_share, Bps(10_000));
    }

    #[test]
    fn snapshot_roundtrip_preserves_curve() {
        let mut book = MarketBook::default();
        book.on_create(&create("M", "CR", 1_000));
        let mut c = CurveState::fresh();
        book.on_trade(&trade(&mut c, "M", "A", true, 700_000_000, 101, 1_001));
        let snap = book.tokens["M"].snapshot();
        let mut b2 = MarketBook::default();
        b2.restore(&snap);
        assert_eq!(b2.tokens["M"].curve, book.tokens["M"].curve);
        assert_eq!(b2.tokens["M"].snapshot(), snap);
    }

    #[test]
    fn untracked_and_eviction() {
        let mut book = MarketBook::default();
        let mut c = CurveState::fresh();
        assert!(book.on_trade(&trade(&mut c, "X", "A", true, 1_000_000, 1, 1)).is_none());
        assert_eq!(book.untracked_trades, 1);
        book.on_create(&create("OLD", "C", 0));
        book.on_create(&create("KEEP", "C", 0));
        book.on_create(&create("NEW", "C", 1_000));
        assert_eq!(book.evict(1_000_000, 500_000, |m| m == "KEEP"), 1);
        assert!(book.tokens.contains_key("KEEP") && book.tokens.contains_key("NEW"));
    }
}
