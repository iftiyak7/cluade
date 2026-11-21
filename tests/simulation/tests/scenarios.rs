use pf_config::TradingConfig;
use pf_intel::TokenFacts;
use pf_sim::{run, Flow, Market};
use pf_types::{Bps, Lamports};

const SOL: u64 = 1_000_000_000;

fn cfg() -> TradingConfig {
    let mut c = TradingConfig::default();
    c.switches.master = true;
    c.switches.auto_buy = true;
    c.scoring.min_momentum = 40;
    c
}

fn clean() -> TokenFacts {
    TokenFacts { top10_concentration: Bps::percent(15), creator_holding: Bps::percent(3), sell_observed: true, ..Default::default() }
}

/// `up` ticks of steady buying, then `down` ticks of selling.
fn pump_then_fade(up: usize, down: usize, buy: u64, sell_bps: u32) -> Vec<Vec<Flow>> {
    let mut f = Vec::new();
    for _ in 0..up {
        f.push(vec![Flow::Buy(Lamports(buy)), Flow::Buy(Lamports(buy / 2))]);
    }
    for _ in 0..down {
        f.push(vec![Flow::SellBps(sell_bps)]);
    }
    f
}

#[test]
fn pump_scenario_takes_profit_and_accounting_balances() {
    let c = cfg();
    let mut markets = vec![Market::new("PUMP", clean(), pump_then_fade(40, 40, SOL / 2, 1_500))];
    let r = run(&c, &mut markets, 80, Lamports(2 * SOL));
    assert_eq!(r.entries, 1, "{r:?}");
    assert_eq!(r.closed, 1, "{r:?}");
    assert!(r.realized_pnl > 0, "pump scenario should exit in profit: {r:?}");
    // accounting invariant: wallet delta == realised pnl, to the lamport
    assert_eq!(r.wallet_end as i64 - r.wallet_start as i64, r.realized_pnl, "{r:?}");
}

#[test]
fn rug_scenario_exits_and_loss_is_recorded() {
    let c = cfg();
    // insiders accumulate a large stake at launch, retail pumps, insiders dump
    let mut flow = pump_then_fade(12, 0, SOL / 2, 0);
    flow[0].push(Flow::Buy(Lamports(8 * SOL)));
    flow.push(vec![Flow::SellBps(9_500)]); // insiders dump 95%
    flow.extend((0..10).map(|_| vec![]));
    let mut markets = vec![Market::new("RUG", clean(), flow)];
    let r = run(&c, &mut markets, 23, Lamports(2 * SOL));
    eprintln!("rug report: {r:?}");
    assert_eq!(r.entries, 1, "{r:?}");
    assert_eq!(r.closed, 1);
    assert!(r.realized_pnl < 0, "{r:?}");
    assert!(r.exit_reasons[0].contains("HardStopLoss") || r.exit_reasons[0].contains("LiquidityDrop"), "{r:?}");
    assert_eq!(r.wallet_end as i64 - r.wallet_start as i64, r.realized_pnl);
}

#[test]
fn rejected_token_is_never_bought() {
    let c = cfg();
    let bad = TokenFacts { freeze_authority_present: true, ..clean() };
    let mut markets = vec![Market::new("FRZ", bad, pump_then_fade(40, 0, SOL / 2, 0))];
    let r = run(&c, &mut markets, 40, Lamports(2 * SOL));
    assert_eq!(r.entries, 0);
    assert_eq!(r.wallet_end, r.wallet_start);
}

#[test]
fn many_tokens_respect_portfolio_limits() {
    let c = cfg();
    let mut markets: Vec<Market> = (0..12).map(|i| Market::new(&format!("T{i}"), clean(), pump_then_fade(30, 30, SOL / 3, 1_000))).collect();
    let r = run(&c, &mut markets, 60, Lamports(SOL / 2));
    assert!(r.max_open_seen <= c.risk.max_open_positions as usize, "{r:?}");
    assert!(r.max_exposure_seen <= c.risk.max_total_exposure.0, "{r:?}");
    assert!(r.min_wallet_seen >= c.risk.min_wallet_reserve.0, "reserve breached: {r:?}");
    assert!(r.denied_entries > 0, "limits should have denied some entries");
    assert_eq!(r.wallet_end as i64 - r.wallet_start as i64, r.realized_pnl, "{r:?}");
}
