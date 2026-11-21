//! Deterministic paper-trading simulation wiring the real modules together:
//! token-intelligence → risk-engine → bonding curve (paper fills) →
//! position-manager → risk-engine accounting.
//!
//! This is the integration harness for Buildings 04–07. It is NOT a
//! backtest: market flow is synthetic and exists to exercise code paths and
//! accounting invariants, not to estimate profitability.

use pf_config::TradingConfig;
use pf_intel::{assess, MarketStats, TokenFacts};
use pf_position::{ExitOrder, Position, Tick};
use pf_risk::{BuyRequest, Portfolio, RiskState, SellFill, SellRequest};
use pf_solana::curve::CurveState;
use pf_types::{Bps, Lamports, TokenAmount, TradingMode};
use std::collections::BTreeMap;

pub const CURVE_FEE: Bps = Bps(100); // placeholder; real fee read from chain
pub const NETWORK_FEE: Lamports = Lamports(5_000 + 100_000); // base + priority (paper)

/// External order flow for one tick: positive = buy SOL, negative = sell tokens fraction (bps of circulating held by others).
#[derive(Debug, Clone, Copy)]
pub enum Flow {
    Buy(Lamports),
    SellBps(u32),
}

pub struct Market {
    pub mint: String,
    pub curve: CurveState,
    pub facts: TokenFacts,
    pub others_tokens: u64,
    pub price_hist: Vec<u64>,
    pub flow: Vec<Vec<Flow>>,
}

impl Market {
    pub fn new(mint: &str, facts: TokenFacts, flow: Vec<Vec<Flow>>) -> Self {
        let c = CurveState::fresh();
        Self { mint: mint.into(), curve: c, facts, others_tokens: 0, price_hist: vec![c.virtual_sol_reserves], flow }
    }
    fn step(&mut self, t: usize) {
        if let Some(flows) = self.flow.get(t).cloned() {
            for f in flows {
                match f {
                    Flow::Buy(l) => {
                        if let Ok(q) = self.curve.apply_buy(l, CURVE_FEE) {
                            self.others_tokens += q.tokens_out.0;
                        }
                    }
                    Flow::SellBps(b) => {
                        let amt = TokenAmount(self.others_tokens).mul_bps(Bps(b));
                        if amt.0 > 0 && self.curve.apply_sell(amt, CURVE_FEE).is_ok() {
                            self.others_tokens -= amt.0;
                        }
                    }
                }
            }
        }
        self.price_hist.push(self.curve.spot_price_scaled() as u64);
    }
    fn change_bps(&self, back: usize) -> i64 {
        let n = self.price_hist.len();
        let then = self.price_hist[n.saturating_sub(back + 1)];
        let now = self.price_hist[n - 1];
        if then == 0 { 0 } else { (now as i128 - then as i128) as i64 * 10_000 / then as i64 }
    }
    fn stats(&self, t: usize) -> MarketStats {
        let recent: Vec<&Flow> = self.flow.iter().skip(t.saturating_sub(5)).take(6).flatten().collect();
        let buys = recent.iter().filter(|f| matches!(f, Flow::Buy(_))).count() as u32;
        let buy_vol: u64 = recent.iter().map(|f| if let Flow::Buy(l) = f { l.0 } else { 0 }).sum();
        let sells = recent.len() as u32 - buys;
        MarketStats {
            age_secs: t as u64 * 10,
            unique_buyers_1m: buys * 3,
            unique_buyers_5m: buys * 8,
            buy_volume_1m: Lamports(buy_vol),
            sell_volume_1m: Lamports(sells as u64 * 300_000_000),
            trades_1m: recent.len() as u32 * 4,
            price_change_1m: self.change_bps(6),
            price_change_5m: self.change_bps(30),
            real_sol_reserves: Lamports(self.curve.real_sol_reserves),
            holders: 20 + t as u32 * 3,
            wash_share: Bps(0),
            base_order_impact: Bps(30),
        }
    }
    fn momentum(&self) -> u8 {
        (50 + self.change_bps(3) / 100).clamp(0, 100) as u8
    }
}

#[derive(Debug, Default)]
pub struct Report {
    pub entries: u32,
    pub denied_entries: u32,
    pub closed: u32,
    pub realized_pnl: i64,
    pub wallet_start: u64,
    pub wallet_end: u64,
    pub max_exposure_seen: u64,
    pub max_open_seen: usize,
    pub min_wallet_seen: u64,
    pub exit_reasons: Vec<String>,
}

pub fn run(cfg: &TradingConfig, markets: &mut [Market], ticks: usize, wallet: Lamports) -> Report {
    let mut risk = RiskState::new(TradingMode::Paper, 0, wallet);
    let mut wallet = wallet.0;
    let mut positions: BTreeMap<String, Position> = BTreeMap::new();
    let mut rep = Report { wallet_start: wallet, min_wallet_seen: wallet, ..Default::default() };

    for t in 0..ticks {
        let now = t as i64 * 10_000;
        for m in markets.iter_mut() {
            m.step(t);
        }
        // ---- exits first (capital protection before new risk)
        for m in markets.iter_mut() {
            let Some(p) = positions.get_mut(&m.mint) else { continue };
            if !p.is_active() {
                continue;
            }
            let value = m.curve.liquidation_value(p.tokens_held, CURVE_FEE);
            let tick = Tick { now, value, liquidity: Lamports(m.curve.real_sol_reserves), momentum: m.momentum(), suspicious: false, portfolio_risk_exit: false };
            if let Some(order) = p.evaluate(cfg, &tick) {
                execute_sell(cfg, &mut risk, p, m, &order, now, &mut wallet, &mut rep);
            }
        }
        // ---- portfolio snapshot at liquidation value
        let (mut open_value, mut unreal) = (0u64, 0i64);
        for m in markets.iter() {
            if let Some(p) = positions.get(&m.mint).filter(|p| p.is_active()) {
                let v = m.curve.liquidation_value(p.tokens_held, CURVE_FEE);
                open_value += v.0;
                unreal += p.unrealized_pnl(v);
            }
        }
        // ---- entries
        for m in markets.iter_mut() {
            if positions.contains_key(&m.mint) {
                continue;
            }
            let a = assess(cfg, &m.facts, &m.stats(t));
            if !a.eligible {
                continue;
            }
            let size = cfg.sizing.base_size;
            let quote = match m.curve.quote_buy(size, CURVE_FEE) { Ok(q) => q, Err(_) => continue };
            let req = BuyRequest {
                intent_id: format!("{}:entry", m.mint),
                mint: m.mint.clone(),
                mode: TradingMode::Paper,
                size,
                risk_class: a.risk_class,
                max_slippage: Bps::percent(5),
                price_impact: quote.price_impact,
                est_tx_fee: NETWORK_FEE,
                market_data_age_ms: 0,
            };
            let d = risk.evaluate_buy(cfg, &req, &Portfolio { wallet_balance: Lamports(wallet), open_value: Lamports(open_value), unrealized_pnl: unreal }, now);
            let Some(approved) = d.approved_size() else {
                rep.denied_entries += 1;
                continue;
            };
            let mut p = Position::new(format!("pos-{}", m.mint), m.mint.clone(), now);
            let q = m.curve.apply_buy(approved, CURVE_FEE).expect("quoted");
            let cost = q.sol_cost.0 + NETWORK_FEE.0;
            wallet -= cost;
            p.on_entry_filled(Lamports(cost), q.tokens_out, Lamports(m.curve.real_sol_reserves), now).unwrap();
            risk.on_buy_filled(&req.intent_id, Lamports(cost), Lamports(q.fee.0 + NETWORK_FEE.0));
            open_value += m.curve.liquidation_value(q.tokens_out, CURVE_FEE).0;
            positions.insert(m.mint.clone(), p);
            rep.entries += 1;
        }
        rep.max_exposure_seen = rep.max_exposure_seen.max(risk.total_exposure().0);
        rep.max_open_seen = rep.max_open_seen.max(positions.values().filter(|p| p.is_active()).count());
        rep.min_wallet_seen = rep.min_wallet_seen.min(wallet);
    }
    // ---- liquidate anything left via emergency exit (end of simulation)
    for m in markets.iter_mut() {
        if let Some(p) = positions.get_mut(&m.mint) {
            if let Some(order) = p.emergency_exit(ticks as i64 * 10_000) {
                execute_sell(cfg, &mut risk, p, m, &order, ticks as i64 * 10_000, &mut wallet, &mut rep);
            }
        }
    }
    rep.wallet_end = wallet;
    rep
}

#[allow(clippy::too_many_arguments)]
fn execute_sell(cfg: &TradingConfig, risk: &mut RiskState, p: &mut Position, m: &mut Market, order: &ExitOrder, now: i64, wallet: &mut u64, rep: &mut Report) {
    let req = SellRequest { intent_id: order.intent_id.clone(), mint: m.mint.clone(), mode: TradingMode::Paper, max_slippage: cfg.risk.max_slippage, est_tx_fee: NETWORK_FEE, urgent: order.urgent };
    if !risk.evaluate_sell(cfg, &req, now).is_approved() {
        p.on_exit_failed(&order.intent_id, now).unwrap();
        return;
    }
    // Paper fill moves the simulated market, like a real sell would.
    let proceeds = match m.curve.apply_sell(order.tokens, CURVE_FEE) {
        Ok(q) => q.sol_out.0.saturating_sub(NETWORK_FEE.0),
        Err(_) => {
            p.on_exit_failed(&order.intent_id, now).unwrap();
            risk.on_exec_failure(cfg);
            return;
        }
    };
    *wallet += proceeds;
    let out = p.on_exit_filled(&order.intent_id, order.tokens, Lamports(proceeds), now).unwrap();
    risk.on_sell_filled(cfg, &SellFill { mint: m.mint.clone(), cost_released: out.cost_released, proceeds: out.proceeds, fees: NETWORK_FEE, position_closed: out.closed, position_pnl: out.position_pnl });
    if out.closed {
        rep.closed += 1;
        rep.realized_pnl += out.position_pnl;
        rep.exit_reasons.push(format!("{}: {:?}", m.mint, p.exit_reason));
    }
}
