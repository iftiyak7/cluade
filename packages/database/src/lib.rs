//! Postgres persistence for trading-core.
//!
//! * [`Db::migrate`] applies `database/migrations/*.sql` in order, recording
//!   each in `schema_migrations` (idempotent across restarts).
//! * [`Db::apply`] writes a batch of [`DbOp`]s in one transaction.
//! * [`Db::load_snapshot`] returns the last engine snapshot for restart.
//!
//! u64 amounts that may exceed i64 (token base units) go through NUMERIC
//! via text casts; timestamps are passed as epoch milliseconds.

mod ops;
pub use ops::DbOp;

use anyhow::{Context, Result};
use std::collections::HashMap;
use std::path::Path;
use tokio_postgres::{Client, NoTls};

pub struct Db {
    client: Client,
    /// signature -> next instruction index (a tx can carry several trades)
    ix_seq: HashMap<String, i32>,
}

impl Db {
    /// Connect without TLS: the database is only reachable on the internal
    /// Docker network (ARCHITECTURE.md §2). Never expose it publicly.
    pub async fn connect(url: &str) -> Result<Self> {
        let (client, conn) = tokio_postgres::connect(url, NoTls).await.context("connecting to Postgres")?;
        tokio::spawn(async move {
            if let Err(e) = conn.await {
                tracing::error!("postgres connection error: {e}");
            }
        });
        Ok(Self { client, ix_seq: HashMap::new() })
    }

    pub async fn migrate(&self, dir: &Path) -> Result<Vec<String>> {
        self.client.batch_execute("CREATE TABLE IF NOT EXISTS schema_migrations (version TEXT PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())").await?;
        let done: Vec<String> = self.client.query("SELECT version FROM schema_migrations", &[]).await?.iter().map(|r| r.get(0)).collect();
        let mut files: Vec<_> = std::fs::read_dir(dir).with_context(|| format!("reading {}", dir.display()))?.filter_map(|e| e.ok()).map(|e| e.path()).filter(|p| p.extension().is_some_and(|x| x == "sql")).collect();
        files.sort();
        let mut applied = Vec::new();
        for f in files {
            let v = f.file_name().unwrap().to_string_lossy().to_string();
            if done.contains(&v) {
                continue;
            }
            let sql = std::fs::read_to_string(&f)?;
            self.client.batch_execute(&sql).await.with_context(|| format!("applying {v}"))?;
            self.client.execute("INSERT INTO schema_migrations(version) VALUES ($1)", &[&v]).await?;
            applied.push(v);
        }
        Ok(applied)
    }

    pub async fn load_snapshot(&self, mode: &str) -> Result<Option<serde_json::Value>> {
        let row = self.client.query_opt("SELECT snapshot FROM engine_snapshots WHERE mode = $1::text::trading_mode", &[&mode]).await?;
        Ok(row.map(|r| r.get(0)))
    }

    pub async fn count(&self, table: &str) -> Result<i64> {
        anyhow::ensure!(table.chars().all(|c| c.is_ascii_alphanumeric() || c == '_'), "bad table name");
        Ok(self.client.query_one(&format!("SELECT count(*) FROM {table}"), &[]).await?.get(0))
    }

    /// Apply a batch atomically. On error the whole batch is rolled back
    /// and the error returned (the caller decides whether to retry).
    pub async fn apply(&mut self, ops: &[DbOp]) -> Result<()> {
        if ops.is_empty() {
            return Ok(());
        }
        if self.ix_seq.len() > 100_000 {
            self.ix_seq.clear();
        }
        let tx = self.client.transaction().await?;
        for op in ops {
            write_op(&tx, &mut self.ix_seq, op).await.with_context(|| format!("writing {}", op_name(op)))?;
        }
        tx.commit().await?;
        Ok(())
    }
}

fn op_name(op: &DbOp) -> &'static str {
    match op {
        DbOp::Token { .. } => "token",
        DbOp::Trade { .. } => "trade",
        DbOp::CurveComplete { .. } => "curve_complete",
        DbOp::Score { .. } => "score",
        DbOp::Intent { .. } => "intent",
        DbOp::RiskDecision { .. } => "risk_decision",
        DbOp::Order { .. } => "order",
        DbOp::PaperFill { .. } => "paper_fill",
        DbOp::Position { .. } => "position",
        DbOp::PositionEvent { .. } => "position_event",
        DbOp::Snapshot(_) => "snapshot",
    }
}

fn i(v: u64) -> i64 {
    i64::try_from(v).unwrap_or(i64::MAX)
}

async fn write_op(tx: &tokio_postgres::Transaction<'_>, ix_seq: &mut HashMap<String, i32>, op: &DbOp) -> Result<()> {
    match op {
        DbOp::Token { mint, symbol, name, uri, creator, bonding_curve, created_slot, created_ms, quote_mint, token_program, mayhem, discovery_latency_us } => {
            tx.execute(
                "INSERT INTO tokens (mint, symbol, name, uri, creator, bonding_curve, created_slot, created_at, discovery_latency_us, quote_mint, token_program, mayhem)
                 VALUES ($1,$2,$3,$4,$5,$6,$7,to_timestamp($8::float8/1000),$9,$10,$11,$12) ON CONFLICT (mint) DO NOTHING",
                &[mint, symbol, name, uri, creator, bonding_curve, &i(*created_slot), &(*created_ms as f64), discovery_latency_us, quote_mint, token_program, mayhem],
            ).await?;
        }
        DbOp::Trade { signature, mint, trader, is_buy, quote_amount, token_amount, virtual_quote_reserves, virtual_token_reserves, slot, block_time, quote_mint, fees, ix_name } => {
            let idx = ix_seq.entry(signature.clone()).or_insert(0);
            let ix = *idx;
            *idx += 1;
            tx.execute(
                "INSERT INTO token_trades (signature, ix_index, mint, trader, side, sol_amount, token_amount, virtual_sol_reserves, virtual_token_reserves, slot, block_time, quote_mint, fees, ix_name)
                 VALUES ($1,$2,$3,$4,$5::text::order_side,$6,$7::text::numeric,$8::text::numeric,$9::text::numeric,$10,to_timestamp($11::float8),$12,$13,$14) ON CONFLICT DO NOTHING",
                &[signature, &ix, mint, trader, &(if *is_buy { "buy" } else { "sell" }), &i(*quote_amount), &token_amount.to_string(), &virtual_quote_reserves.to_string(), &virtual_token_reserves.to_string(), &i(*slot), &block_time.map(|b| b as f64), quote_mint, &i(*fees), ix_name],
            ).await?;
        }
        DbOp::CurveComplete { mint, at_ms } => {
            tx.execute("UPDATE tokens SET curve_complete = true, completed_at = to_timestamp($2::float8/1000) WHERE mint = $1", &[mint, &(*at_ms as f64)]).await?;
        }
        DbOp::Score { mint, safety, momentum, liquidity, opportunity, hard_rejects, factors } => {
            tx.execute(
                "INSERT INTO token_scores (mint, safety_score, momentum_score, liquidity_score, opportunity_score, hard_rejects, factors) VALUES ($1,$2,$3,$4,$5,$6,$7)",
                &[mint, &(*safety as i16), &(*momentum as i16), &(*liquidity as i16), &(*opportunity as i16), hard_rejects, factors],
            ).await?;
        }
        DbOp::Intent { intent_id, mint, strategy, buy_mode, size, max_slippage_bps, created_ms, expires_ms } => {
            tx.execute(
                "INSERT INTO entry_intents (intent_id, mint, strategy, buy_mode, mode, size_lamports, max_slippage_bps, created_at, expires_at)
                 VALUES ($1::text::uuid,$2,$3,$4,'paper',$5,$6,to_timestamp($7::float8/1000),to_timestamp($8::float8/1000)) ON CONFLICT DO NOTHING",
                &[intent_id, mint, strategy, buy_mode, &i(*size), &(*max_slippage_bps as i32), &(*created_ms as f64), &(*expires_ms as f64)],
            ).await?;
        }
        DbOp::RiskDecision { intent_id, side, approved, size, reasons } => {
            tx.execute(
                "INSERT INTO risk_decisions (intent_id, side, verdict, approved_lamports, reasons) VALUES ($1,$2::text::order_side,$3::text::risk_verdict,$4,$5) ON CONFLICT DO NOTHING",
                &[intent_id, side, &(if *approved { "approved" } else { "denied" }), &size.map(i), reasons],
            ).await?;
        }
        DbOp::Order { order_id, intent_id, side, mint, position_id, requested_lamports, requested_tokens, min_out, status, failure_reason } => {
            tx.execute(
                "INSERT INTO orders (order_id, intent_id, side, mode, mint, position_id, requested_lamports, requested_tokens, min_out, status, failure_reason)
                 VALUES ($1::text::uuid,$2,$3::text::order_side,'paper',$4,$5::text::uuid,$6,$7::text::numeric,$8::text::numeric,$9::text::order_status,$10)
                 ON CONFLICT (order_id) DO UPDATE SET status = EXCLUDED.status, failure_reason = EXCLUDED.failure_reason, updated_at = now()",
                &[order_id, intent_id, side, mint, position_id, &requested_lamports.map(i), &requested_tokens.map(|t| t.to_string()), &min_out.to_string(), status, failure_reason],
            ).await?;
        }
        DbOp::PaperFill { order_id, slot, at_ms, sol_delta, token_delta, curve_fees, network_fee, price_impact_bps } => {
            tx.execute(
                "INSERT INTO paper_fills (order_id, slot, at, sol_delta, token_delta, curve_fees, network_fee, price_impact_bps) VALUES ($1::text::uuid,$2,to_timestamp($3::float8/1000),$4,$5::text::numeric,$6,$7,$8)",
                &[order_id, &i(*slot), &(*at_ms as f64), sol_delta, &token_delta.to_string(), &i(*curve_fees), &i(*network_fee), &(*price_impact_bps as i32)],
            ).await?;
        }
        DbOp::Position { position_id, mint, state, strategy, entry_intent_id, cost, tokens_held, tokens_bought, proceeds, peak_value, tp_levels_hit, opened_ms, closed_ms, exit_reason, state_json } => {
            tx.execute(
                "INSERT INTO positions (position_id, mint, mode, strategy, state, entry_intent_id, cost_lamports, tokens_held, tokens_bought, proceeds_lamports, peak_value_lamports, tp_levels_hit, opened_at, closed_at, exit_reason, state_json)
                 VALUES ($1::text::uuid,$2,'paper',$3,$4::text::position_state,$5::text::uuid,$6,$7::text::numeric,$8::text::numeric,$9,$10,$11,to_timestamp($12::float8/1000),to_timestamp($13::float8/1000),$14,$15)
                 ON CONFLICT (position_id) DO UPDATE SET state = EXCLUDED.state, cost_lamports = EXCLUDED.cost_lamports, tokens_held = EXCLUDED.tokens_held,
                   proceeds_lamports = EXCLUDED.proceeds_lamports, peak_value_lamports = EXCLUDED.peak_value_lamports, tp_levels_hit = EXCLUDED.tp_levels_hit,
                   closed_at = EXCLUDED.closed_at, exit_reason = EXCLUDED.exit_reason, state_json = EXCLUDED.state_json, version = positions.version + 1",
                &[position_id, mint, strategy, state, entry_intent_id, &i(*cost), &tokens_held.to_string(), &tokens_bought.to_string(), &i(*proceeds), &i(*peak_value), &(*tp_levels_hit as i16), &opened_ms.map(|m| m as f64), &closed_ms.map(|m| m as f64), exit_reason, state_json],
            ).await?;
        }
        DbOp::PositionEvent { position_id, from, to, reason, at_ms } => {
            tx.execute(
                "INSERT INTO position_events (position_id, from_state, to_state, reason, at) VALUES ($1::text::uuid,$2::text::position_state,$3::text::position_state,$4,to_timestamp($5::float8/1000))",
                &[position_id, from, to, reason, &(*at_ms as f64)],
            ).await?;
        }
        DbOp::Snapshot(v) => {
            tx.execute(
                "INSERT INTO engine_snapshots (mode, snapshot, updated_at) VALUES ('paper', $1, now()) ON CONFLICT (mode) DO UPDATE SET snapshot = EXCLUDED.snapshot, updated_at = now()",
                &[v],
            ).await?;
        }
    }
    Ok(())
}
