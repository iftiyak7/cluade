//! Performance metrics over closed trades (Building 10).
//!
//! All money in lamports (i64). Metrics describe what happened; they are
//! not forecasts.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ClosedTrade {
    pub mint: String,
    pub opened_ms: i64,
    pub closed_ms: i64,
    /// SOL spent incl. curve fees and network fees (excl. refundable rent).
    pub cost: i64,
    /// SOL received net of all fees.
    pub proceeds: i64,
    /// Curve + network fees paid across entry and exits.
    pub fees: i64,
    pub exit_reason: String,
}

impl ClosedTrade {
    pub fn pnl(&self) -> i64 {
        self.proceeds - self.cost
    }
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
pub struct Summary {
    pub trades: usize,
    pub wins: usize,
    pub losses: usize,
    pub win_rate_pct: f64,
    pub gross_profit: i64,
    pub gross_loss: i64,
    pub net_pnl: i64,
    /// gross_profit / |gross_loss|; None when there are no losses.
    pub profit_factor: Option<f64>,
    pub avg_win: i64,
    pub avg_loss: i64,
    pub largest_loss: i64,
    pub fees: i64,
    pub avg_hold_secs: f64,
    /// Largest peak-to-trough fall of cumulative realised PnL.
    pub max_drawdown: i64,
    pub total_cost: i64,
    /// net_pnl / total capital deployed (sum of costs), in percent.
    pub return_on_deployed_pct: f64,
}

pub fn summarize(trades: &[ClosedTrade]) -> Summary {
    if trades.is_empty() {
        return Summary::default();
    }
    let mut sorted: Vec<&ClosedTrade> = trades.iter().collect();
    sorted.sort_by_key(|t| t.closed_ms);
    let (mut gp, mut gl, mut wins, mut losses) = (0i64, 0i64, 0usize, 0usize);
    let (mut cum, mut peak, mut mdd) = (0i64, 0i64, 0i64);
    let mut largest_loss = 0i64;
    for t in &sorted {
        let p = t.pnl();
        if p > 0 {
            gp += p;
            wins += 1;
        } else {
            gl += p;
            losses += 1;
            largest_loss = largest_loss.min(p);
        }
        cum += p;
        peak = peak.max(cum);
        mdd = mdd.max(peak - cum);
    }
    let n = trades.len();
    let cost: i64 = trades.iter().map(|t| t.cost).sum();
    Summary {
        trades: n,
        wins,
        losses,
        win_rate_pct: wins as f64 * 100.0 / n as f64,
        gross_profit: gp,
        gross_loss: gl,
        net_pnl: gp + gl,
        profit_factor: (gl != 0).then(|| gp as f64 / (-gl) as f64),
        avg_win: if wins > 0 { gp / wins as i64 } else { 0 },
        avg_loss: if losses > 0 { gl / losses as i64 } else { 0 },
        largest_loss,
        fees: trades.iter().map(|t| t.fees).sum(),
        avg_hold_secs: trades.iter().map(|t| (t.closed_ms - t.opened_ms) as f64 / 1000.0).sum::<f64>() / n as f64,
        max_drawdown: mdd,
        total_cost: cost,
        return_on_deployed_pct: if cost > 0 { (gp + gl) as f64 * 100.0 / cost as f64 } else { 0.0 },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn t(cost: i64, proceeds: i64, at: i64) -> ClosedTrade {
        ClosedTrade { mint: "m".into(), opened_ms: at - 10_000, closed_ms: at, cost, proceeds, fees: 1, exit_reason: "x".into() }
    }

    #[test]
    fn summary_metrics() {
        let s = summarize(&[t(100, 150, 1), t(100, 60, 2), t(100, 80, 3), t(100, 130, 4)]);
        assert_eq!((s.trades, s.wins, s.losses), (4, 2, 2));
        assert_eq!(s.net_pnl, 20);
        assert_eq!(s.gross_profit, 80);
        assert_eq!(s.gross_loss, -60);
        assert!((s.profit_factor.unwrap() - 80.0 / 60.0).abs() < 1e-9);
        assert_eq!(s.max_drawdown, 60, "cum: 50, 10, -10, 20 -> peak 50 trough -10");
        assert_eq!(s.largest_loss, -40);
        assert_eq!(s.avg_hold_secs, 10.0);
        assert_eq!(s.return_on_deployed_pct, 5.0);
    }

    #[test]
    fn empty_and_no_losses() {
        assert_eq!(summarize(&[]).trades, 0);
        assert_eq!(summarize(&[t(1, 2, 1)]).profit_factor, None);
    }
}
