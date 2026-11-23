//! pf-trader — paper-trading driver for trading-core.
//!
//! Sources:
//!   --source blocks|ws     live mainnet discovery (pf-discovery), paper fills
//!   --source replay        re-run recorded envelopes deterministically
//!
//! Options:
//!   --config FILE          TradingConfig JSON (validated; live mode refused)
//!   --duration SECS        stop after N seconds (live) — default: run until Ctrl-C
//!   --record FILE          append every envelope (JSONL) for later replay
//!   --replay FILE          envelopes to replay (with --source replay)
//!   --mint-cache FILE      mint accounts seen live (JSONL); written live, read on replay
//!   --journal FILE         decision log (JSONL)
//!   --report FILE          final report (JSON)
//!   --fresh                ignore any saved engine snapshot
//!
//! Environment: RPC_PRIMARY_HTTP / RPC_SECONDARY_HTTP / RPC_*_WS, RPC_MAX_RPS,
//! DATABASE_URL (optional; enables persistence + restart recovery).

use anyhow::{bail, Context, Result};
use pf_config::TradingConfig;
use pf_db::{Db, DbOp};
use pf_discovery::pipeline::{Pipeline, Received};
use pf_discovery::{env_list, redact, rpc::RpcPool, sources};
use pf_events::Envelope;
use pf_solana::token::MintInfo;
use pf_trading_core::engine::{Effect, Engine, EngineSnapshot};
use pf_trading_core::replay::{self, decode_mint_value};
use serde_json::{json, Value};
use std::collections::HashMap;
use std::io::Write;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};
use tokio::sync::{mpsc, watch};

#[derive(Default)]
struct Args {
    source: String,
    config: Option<PathBuf>,
    duration: Option<u64>,
    record: Option<PathBuf>,
    replay: Option<PathBuf>,
    mint_cache: Option<PathBuf>,
    journal: Option<PathBuf>,
    report: Option<PathBuf>,
    fresh: bool,
}

fn parse_args() -> Result<Args> {
    let mut a = Args { source: "blocks".into(), ..Default::default() };
    let mut it = std::env::args().skip(1);
    while let Some(k) = it.next() {
        let mut v = || it.next().ok_or_else(|| anyhow::anyhow!("{k} needs a value"));
        match k.as_str() {
            "--source" => a.source = v()?,
            "--config" => a.config = Some(v()?.into()),
            "--duration" => a.duration = Some(v()?.parse()?),
            "--record" => a.record = Some(v()?.into()),
            "--replay" => a.replay = Some(v()?.into()),
            "--mint-cache" => a.mint_cache = Some(v()?.into()),
            "--journal" => a.journal = Some(v()?.into()),
            "--report" => a.report = Some(v()?.into()),
            "--fresh" => a.fresh = true,
            "-h" | "--help" => {
                eprintln!("{}", include_str!("main.rs").lines().take_while(|l| l.starts_with("//!")).map(|l| l.trim_start_matches("//!")).collect::<Vec<_>>().join("\n"));
                std::process::exit(0);
            }
            _ => bail!("unknown argument {k}"),
        }
    }
    if !["blocks", "ws", "replay"].contains(&a.source.as_str()) {
        bail!("--source must be blocks, ws or replay");
    }
    if a.source == "replay" && a.replay.is_none() {
        bail!("--source replay needs --replay FILE");
    }
    Ok(a)
}

fn wall_ms() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as i64).unwrap_or(0)
}

fn load_config(p: &Option<PathBuf>) -> Result<TradingConfig> {
    let cfg: TradingConfig = match p {
        Some(p) => serde_json::from_str(&std::fs::read_to_string(p).with_context(|| format!("reading {}", p.display()))?)?,
        None => TradingConfig::default(),
    };
    if let Err(e) = cfg.validate() {
        bail!("invalid config: {e:?}");
    }
    Ok(cfg)
}

fn append_line(f: &mut Option<std::fs::File>, v: &impl serde::Serialize) {
    if let Some(f) = f {
        if let Ok(mut s) = serde_json::to_vec(v) {
            s.push(b'\n');
            let _ = f.write_all(&s);
        }
    }
}

fn open_append(p: &Option<PathBuf>) -> Result<Option<std::fs::File>> {
    Ok(match p {
        Some(p) => Some(std::fs::OpenOptions::new().create(true).append(true).open(p).with_context(|| format!("opening {}", p.display()))?),
        None => None,
    })
}

struct Sinks {
    db_tx: Option<mpsc::Sender<DbOp>>,
    journal: Option<std::fs::File>,
    mint_req: Option<mpsc::Sender<String>>,
    replay_mints: HashMap<String, Value>,
}

/// Route engine effects. In replay, mint lookups are answered from the cache.
fn route(engine: &mut Engine, sinks: &mut Sinks) {
    loop {
        let effects = engine.drain_effects();
        if effects.is_empty() {
            return;
        }
        for e in effects {
            match e {
                Effect::Db(op) => {
                    if let Some(tx) = &sinks.db_tx {
                        if tx.try_send(*op).is_err() {
                            tracing::warn!("db queue full; dropping write");
                        }
                    }
                }
                Effect::Log(v) => append_line(&mut sinks.journal, &v),
                Effect::FetchMint(m) => match &sinks.mint_req {
                    Some(tx) => {
                        let _ = tx.try_send(m);
                    }
                    None => {
                        let r = sinks.replay_mints.get(&m).map(decode_mint_value).unwrap_or_else(|| Err("not in mint cache".into()));
                        engine.on_mint(&m, r);
                    }
                },
            }
        }
    }
}

fn db_writer(mut db: Db) -> (mpsc::Sender<DbOp>, tokio::task::JoinHandle<u64>) {
    let (tx, mut rx) = mpsc::channel::<DbOp>(200_000);
    let h = tokio::spawn(async move {
        let mut errors = 0u64;
        let mut buf = Vec::with_capacity(1024);
        loop {
            let first = rx.recv().await;
            let closed = first.is_none();
            if let Some(op) = first {
                buf.push(op);
            }
            while buf.len() < 2_000 {
                match rx.try_recv() {
                    Ok(op) => buf.push(op),
                    Err(_) => break,
                }
            }
            if !buf.is_empty() {
                if let Err(e) = db.apply(&buf).await {
                    errors += 1;
                    tracing::error!("db batch of {} failed: {e:#}", buf.len());
                }
                buf.clear();
            }
            if closed {
                return errors;
            }
            tokio::time::sleep(Duration::from_millis(250)).await;
        }
    });
    (tx, h)
}

fn print_report(engine: &Engine, extra: Value, label: &str) -> Value {
    let mut r = serde_json::to_value(engine.report()).unwrap_or_default();
    r["runtime"] = extra;
    eprintln!("{label} {}", serde_json::to_string(&r).unwrap_or_default());
    r
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt().with_writer(std::io::stderr).with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into())).init();
    let args = parse_args()?;
    let cfg = load_config(&args.config)?;

    // ---- persistence + restart recovery
    let mut db_tx = None;
    let mut db_handle = None;
    let mut engine = None;
    if let Ok(url) = std::env::var("DATABASE_URL") {
        let db = Db::connect(&url).await?;
        let dir = std::env::var("MIGRATIONS_DIR").unwrap_or_else(|_| "database/migrations".into());
        let applied = db.migrate(std::path::Path::new(&dir)).await?;
        tracing::info!(?applied, "migrations up to date");
        if !args.fresh && args.source != "replay" {
            if let Some(v) = db.load_snapshot("paper").await? {
                let snap: EngineSnapshot = serde_json::from_value(v).context("decoding engine snapshot")?;
                tracing::info!(positions = snap.positions.len(), closed = snap.closed.len(), wallet = snap.wallet, "restoring from snapshot");
                engine = Some(Engine::restore(cfg.clone(), snap)?);
            }
        }
        let (tx, h) = db_writer(db);
        db_tx = Some(tx);
        db_handle = Some(h);
    }
    let mut engine = match engine {
        Some(e) => e,
        None => Engine::new(cfg.clone())?,
    };
    let mut sinks = Sinks { db_tx, journal: open_append(&args.journal)?, mint_req: None, replay_mints: HashMap::new() };
    let started = Instant::now();

    if args.source == "replay" {
        if let Some(p) = &args.mint_cache {
            sinks.replay_mints = replay::load_mint_cache(&std::fs::read_to_string(p)?);
        }
        let text = std::fs::read_to_string(args.replay.as_ref().unwrap())?;
        let envs: Vec<Envelope> = text.lines().filter(|l| !l.trim().is_empty()).map(serde_json::from_str).collect::<std::result::Result<_, _>>().context("parsing replay file")?;
        let mints = std::mem::take(&mut sinks.replay_mints);
        let n = replay::run(&mut engine, &envs, &mints, |e| match e {
            Effect::Db(op) => {
                if let Some(tx) = &sinks.db_tx {
                    let _ = tx.try_send(*op);
                }
            }
            Effect::Log(v) => append_line(&mut sinks.journal, &v),
            Effect::FetchMint(_) => {}
        });
        let r = print_report(&engine, json!({"source": "replay", "envelopes": n, "elapsed_s": started.elapsed().as_secs_f64()}), "REPORT");
        finish(&args, r, sinks, db_handle).await?;
        return Ok(());
    }

    // ---- live
    let rpc = Arc::new(RpcPool::new(env_list(&["RPC_PRIMARY_HTTP", "RPC_SECONDARY_HTTP"]))?);
    let (rx_tx, mut rx) = mpsc::channel::<Received>(16_384);
    let (tip_tx, tip_rx) = watch::channel(0u64);
    let (gap_tx, mut gap_rx) = mpsc::channel::<(u64, u64)>(64);
    let source = match args.source.as_str() {
        "blocks" => tokio::spawn(sources::blocks::run(rpc.clone(), None, rx_tx.clone(), tip_tx, gap_tx.clone())),
        _ => {
            let r2 = rpc.clone();
            tokio::spawn(async move {
                loop {
                    if let Ok(s) = r2.get_slot("processed").await {
                        let _ = tip_tx.send(s);
                    }
                    tokio::time::sleep(Duration::from_millis(400)).await;
                }
            });
            tokio::spawn(sources::ws::run(env_list(&["RPC_PRIMARY_WS", "RPC_SECONDARY_WS"]), rpc.clone(), rx_tx.clone(), gap_tx.clone()))
        }
    };
    drop(rx_tx);

    // mint inspector: shares the RPC pool (and its rate budget) with discovery
    let (mreq_tx, mut mreq_rx) = mpsc::channel::<String>(4_096);
    let (mres_tx, mut mres_rx) = mpsc::channel::<(String, std::result::Result<MintInfo, String>, Value)>(4_096);
    {
        let rpc = rpc.clone();
        tokio::spawn(async move {
            while let Some(m) = mreq_rx.recv().await {
                let res = rpc.call("getAccountInfo", json!([m, {"encoding": "base64", "commitment": "confirmed"}])).await;
                let (info, raw) = match res {
                    Ok(v) => (decode_mint_value(&v), v),
                    Err(e) => (Err(redact::scrub(&e.to_string(), rpc.secrets())), Value::Null),
                };
                let _ = mres_tx.send((m, info, raw)).await;
            }
        });
    }
    sinks.mint_req = Some(mreq_tx);
    let mut record = open_append(&args.record)?;
    let mut mint_cache = open_append(&args.mint_cache)?;

    let mut pipe = Pipeline::new();
    let mut ticker = tokio::time::interval(Duration::from_secs(1));
    let mut report_every = tokio::time::interval(Duration::from_secs(60));
    report_every.tick().await;
    let deadline = args.duration.map(|d| tokio::time::Instant::now() + Duration::from_secs(d));
    let runtime = |pipe: &Pipeline, rpc: &RpcPool| json!({"source": args.source, "uptime_s": started.elapsed().as_secs(), "discovery": pipe.stats.snapshot(), "rpc": rpc.health(), "rpc_paced": rpc.throttled.load(std::sync::atomic::Ordering::Relaxed)});

    loop {
        tokio::select! {
            r = rx.recv() => {
                let Some(r) = r else { break };
                let tip = *tip_rx.borrow();
                pipe.tip_slot = tip;
                for env in pipe.handle(r) {
                    append_line(&mut record, &env);
                    engine.handle(&env, wall_ms(), tip);
                    route(&mut engine, &mut sinks);
                }
            }
            Some((m, info, raw)) = mres_rx.recv() => {
                if !raw.is_null() {
                    append_line(&mut mint_cache, &json!({"mint": m, "account": raw}));
                }
                engine.on_mint(&m, info);
                route(&mut engine, &mut sinks);
            }
            Some((from, to)) = gap_rx.recv() => tracing::warn!(from, to, "possible missed slots"),
            _ = ticker.tick() => {
                engine.tick(wall_ms(), *tip_rx.borrow());
                route(&mut engine, &mut sinks);
            }
            _ = report_every.tick() => { print_report(&engine, runtime(&pipe, &rpc), "PROGRESS"); }
            _ = async { match deadline { Some(d) => tokio::time::sleep_until(d).await, None => std::future::pending().await } } => break,
            _ = tokio::signal::ctrl_c() => { tracing::info!("shutting down"); break; }
        }
        if source.is_finished() {
            tracing::error!("discovery source stopped");
            break;
        }
    }
    engine.tick(wall_ms(), *tip_rx.borrow());
    route(&mut engine, &mut sinks);
    // final snapshot so a restart resumes exactly here
    if let Some(tx) = &sinks.db_tx {
        let _ = tx.send(DbOp::Snapshot(serde_json::to_value(engine.snapshot())?)).await;
    }
    let r = print_report(&engine, runtime(&pipe, &rpc), "REPORT");
    finish(&args, r, sinks, db_handle).await
}

async fn finish(args: &Args, report: Value, sinks: Sinks, db_handle: Option<tokio::task::JoinHandle<u64>>) -> Result<()> {
    if let Some(p) = &args.report {
        std::fs::write(p, serde_json::to_vec_pretty(&report)?)?;
    }
    drop(sinks);
    if let Some(h) = db_handle {
        let errors = h.await.unwrap_or(0);
        if errors > 0 {
            tracing::error!(errors, "database write errors occurred");
        }
    }
    Ok(())
}
