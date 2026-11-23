//! Integration tests against a real Postgres. Skipped (with a message)
//! unless TEST_DATABASE_URL is set; CI provides one.

use pf_db::{Db, DbOp};
use serde_json::json;
use std::path::PathBuf;

fn migrations() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../database/migrations")
}

async fn db() -> Option<Db> {
    let Ok(url) = std::env::var("TEST_DATABASE_URL") else {
        eprintln!("TEST_DATABASE_URL not set; skipping Postgres integration test");
        return None;
    };
    let db = Db::connect(&url).await.expect("connect");
    db.migrate(&migrations()).await.expect("migrate");
    Some(db)
}

fn token(mint: &str) -> DbOp {
    DbOp::Token {
        mint: mint.into(), symbol: "S".into(), name: "N".into(), uri: "u".into(), creator: "C".into(), bonding_curve: "B".into(),
        created_slot: 1, created_ms: 1_795_000_000_000, quote_mint: "11111111111111111111111111111111".into(), token_program: Some("TokenzQd".into()),
        mayhem: false, discovery_latency_us: 120,
    }
}

#[tokio::test]
async fn migrations_are_idempotent_and_ops_round_trip() {
    let Some(mut db) = db().await else { return };
    // second run applies nothing
    assert!(db.migrate(&migrations()).await.unwrap().is_empty());

    let mint = format!("TEST{}", std::process::id());
    let intent = uuid_like(1);
    let order = uuid_like(2);
    let position = uuid_like(3);
    let ops = vec![
        token(&mint),
        token(&mint), // duplicate create is ignored
        DbOp::Trade {
            signature: format!("sig{mint}"), mint: mint.clone(), trader: "T".into(), is_buy: true, quote_amount: 1_000, token_amount: u64::MAX,
            virtual_quote_reserves: 30_000_001_000, virtual_token_reserves: 1_072_000_000_000_000, slot: 2, block_time: Some(1_795_000_001),
            quote_mint: "11111111111111111111111111111111".into(), fees: 13, ix_name: "buy".into(),
        },
        DbOp::Trade {
            // same signature, second trade in the tx
            signature: format!("sig{mint}"), mint: mint.clone(), trader: "T".into(), is_buy: false, quote_amount: 900, token_amount: 5,
            virtual_quote_reserves: 1, virtual_token_reserves: 1, slot: 2, block_time: None, quote_mint: "x".into(), fees: 1, ix_name: "sell".into(),
        },
        DbOp::Score { mint: mint.clone(), safety: 90, momentum: 60, liquidity: 50, opportunity: 70, hard_rejects: vec![], factors: json!([{"name": "x"}]) },
        DbOp::Intent { intent_id: intent.clone(), mint: mint.clone(), strategy: "default".into(), buy_mode: "smart_entry".into(), size: 50_000_000, max_slippage_bps: 1000, created_ms: 1, expires_ms: 2 },
        DbOp::RiskDecision { intent_id: intent.clone(), side: "buy".into(), approved: true, size: Some(25_000_000), reasons: vec!["clamped".into()] },
        DbOp::Order { order_id: order.clone(), intent_id: intent.clone(), side: "buy".into(), mint: mint.clone(), position_id: Some(position.clone()), requested_lamports: Some(25_000_000), requested_tokens: None, min_out: 1, status: "submitted".into(), failure_reason: None },
        DbOp::Order { order_id: order.clone(), intent_id: intent.clone(), side: "buy".into(), mint: mint.clone(), position_id: Some(position.clone()), requested_lamports: None, requested_tokens: None, min_out: 1, status: "confirmed".into(), failure_reason: None },
        DbOp::PaperFill { order_id: order.clone(), slot: 3, at_ms: 3_000, sol_delta: -27_000_000, token_delta: 123, curve_fees: 300, network_fee: 105_000, price_impact_bps: 12 },
        DbOp::Position { position_id: position.clone(), mint: mint.clone(), state: "OPEN".into(), strategy: "default".into(), entry_intent_id: intent.clone(), cost: 25_105_000, tokens_held: 123, tokens_bought: 123, proceeds: 0, peak_value: 25_105_000, tp_levels_hit: 0, opened_ms: Some(3_000), closed_ms: None, exit_reason: None, state_json: json!({"k": 1}) },
        DbOp::Position { position_id: position.clone(), mint: mint.clone(), state: "CLOSED".into(), strategy: "default".into(), entry_intent_id: intent.clone(), cost: 25_105_000, tokens_held: 0, tokens_bought: 123, proceeds: 30_000_000, peak_value: 31_000_000, tp_levels_hit: 2, opened_ms: Some(3_000), closed_ms: Some(9_000), exit_reason: Some("TrailingStop".into()), state_json: json!({"k": 2}) },
        DbOp::Order { order_id: uuid_like(4), intent_id: format!("{position}:exit:1"), side: "sell".into(), mint: mint.clone(), position_id: Some(position.clone()), requested_lamports: None, requested_tokens: Some(123), min_out: 1, status: "failed".into(), failure_reason: Some("slippage".into()) },
        DbOp::CurveComplete { mint: mint.clone(), at_ms: 10_000 },
        DbOp::Snapshot(json!({"version": 1, "wallet": 42})),
    ];
    db.apply(&ops).await.expect("apply batch");

    assert!(db.count("token_trades").await.unwrap() >= 2);
    let snap = db.load_snapshot("paper").await.unwrap().unwrap();
    assert_eq!(snap["wallet"], 42);
    db.apply(&[DbOp::Snapshot(json!({"version": 1, "wallet": 43}))]).await.unwrap();
    assert_eq!(db.load_snapshot("paper").await.unwrap().unwrap()["wallet"], 43, "snapshot is upserted");
    // invalid batch rolls back entirely
    let bad = vec![token(&format!("{mint}B")), DbOp::Order { order_id: "not-a-uuid".into(), intent_id: "x".into(), side: "buy".into(), mint: "missing".into(), position_id: None, requested_lamports: None, requested_tokens: None, min_out: 0, status: "created".into(), failure_reason: None }];
    assert!(db.apply(&bad).await.is_err());
}

fn uuid_like(n: u32) -> String {
    format!("{:08x}-0000-4000-8000-{:012x}", std::process::id(), n)
}
