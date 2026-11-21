//! pf-discovery — real-time Pump.fun token discovery.
//!
//! Writes one JSON event envelope per line to stdout (TokenDiscovered,
//! TradeObserved, CurveCompleted) and periodic stats (JSON) to stderr.
//!
//! Configuration (environment):
//!   RPC_PRIMARY_HTTP, RPC_SECONDARY_HTTP   HTTP JSON-RPC endpoints
//!   RPC_PRIMARY_WS,  RPC_SECONDARY_WS      WebSocket endpoints (ws source)
//!
//! Usage:
//!   pf-discovery [--source ws|blocks] [--duration SECS] [--start-slot N]
//!                [--stats-every SECS] [--creates-only]

mod pipeline;
mod redact;
mod rpc;
mod sources;
mod stats;

use anyhow::{bail, Result};
use pipeline::{Pipeline, Received};
use pf_events::Event;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::io::AsyncWriteExt;
use tokio::sync::{mpsc, watch};

struct Args {
    source: String,
    duration: Option<u64>,
    start_slot: Option<u64>,
    stats_every: u64,
    creates_only: bool,
}

fn parse_args() -> Result<Args> {
    let mut a = Args { source: "ws".into(), duration: None, start_slot: None, stats_every: 10, creates_only: false };
    let mut it = std::env::args().skip(1);
    while let Some(k) = it.next() {
        let mut val = || it.next().ok_or_else(|| anyhow::anyhow!("{k} needs a value"));
        match k.as_str() {
            "--source" => a.source = val()?,
            "--duration" => a.duration = Some(val()?.parse()?),
            "--start-slot" => a.start_slot = Some(val()?.parse()?),
            "--stats-every" => a.stats_every = val()?.parse()?,
            "--creates-only" => a.creates_only = true,
            "-h" | "--help" => {
                eprintln!("pf-discovery [--source ws|blocks] [--duration SECS] [--start-slot N] [--stats-every SECS] [--creates-only]");
                std::process::exit(0);
            }
            _ => bail!("unknown argument {k}"),
        }
    }
    if !["ws", "blocks"].contains(&a.source.as_str()) {
        bail!("--source must be ws or blocks");
    }
    Ok(a)
}

fn env_list(keys: &[&str]) -> Vec<String> {
    keys.iter().filter_map(|k| std::env::var(k).ok()).filter(|v| !v.trim().is_empty()).collect()
}

#[tokio::main]
async fn main() -> Result<()> {
    tracing_subscriber::fmt().with_writer(std::io::stderr).with_env_filter(tracing_subscriber::EnvFilter::try_from_default_env().unwrap_or_else(|_| "info".into())).init();
    let args = parse_args()?;
    let rpc = Arc::new(rpc::RpcPool::new(env_list(&["RPC_PRIMARY_HTTP", "RPC_SECONDARY_HTTP"]))?);
    tracing::info!(endpoints = ?rpc.health().iter().map(|h| h.label.clone()).collect::<Vec<_>>(), source = %args.source, "starting");

    let (tx, mut rx) = mpsc::channel::<Received>(16_384);
    let (tip_tx, tip_rx) = watch::channel(0u64);
    let (gap_tx, mut gap_rx) = mpsc::channel::<(u64, u64)>(64);

    let source = match args.source.as_str() {
        "blocks" => tokio::spawn(sources::blocks::run(rpc.clone(), args.start_slot, tx.clone(), tip_tx, gap_tx.clone())),
        _ => {
            // Keep the tip current for slot-lag metrics.
            let r2 = rpc.clone();
            tokio::spawn(async move {
                loop {
                    if let Ok(s) = r2.get_slot("processed").await {
                        let _ = tip_tx.send(s);
                    }
                    tokio::time::sleep(Duration::from_millis(400)).await;
                }
            });
            tokio::spawn(sources::ws::run(env_list(&["RPC_PRIMARY_WS", "RPC_SECONDARY_WS"]), rpc.clone(), tx.clone(), gap_tx.clone()))
        }
    };
    drop(tx);

    let mut pipe = Pipeline::new();
    let mut stdout = tokio::io::stdout();
    let started = Instant::now();
    let deadline = args.duration.map(|d| started + Duration::from_secs(d));
    let mut stats_tick = tokio::time::interval(Duration::from_secs(args.stats_every.max(1)));
    stats_tick.tick().await;

    let report = |pipe: &Pipeline, rpc: &rpc::RpcPool, final_: bool| {
        let mut snap = pipe.stats.snapshot();
        snap["uptime_s"] = (started.elapsed().as_secs_f64().round()).into();
        snap["rpc"] = serde_json::to_value(rpc.health()).unwrap_or_default();
        snap["rpc_failovers"] = rpc.failovers.load(std::sync::atomic::Ordering::Relaxed).into();
        snap["rpc_paced"] = rpc.throttled.load(std::sync::atomic::Ordering::Relaxed).into();
        snap["final"] = final_.into();
        eprintln!("STATS {snap}");
    };

    loop {
        let sleep_until = deadline.map(tokio::time::Instant::from_std);
        tokio::select! {
            r = rx.recv() => {
                let Some(r) = r else { break };
                pipe.tip_slot = *tip_rx.borrow();
                for env in pipe.handle(r) {
                    if args.creates_only && !matches!(env.event, Event::TokenDiscovered(_)) { continue; }
                    let mut line = serde_json::to_vec(&env)?;
                    line.push(b'\n');
                    stdout.write_all(&line).await?;
                }
            }
            Some((from, to)) = gap_rx.recv() => {
                pipe.stats.inc("gaps");
                tracing::warn!(from, to, "possible missed slots");
            }
            _ = stats_tick.tick() => { stdout.flush().await?; report(&pipe, &rpc, false); }
            _ = async { match sleep_until { Some(t) => tokio::time::sleep_until(t).await, None => std::future::pending().await } } => break,
            _ = tokio::signal::ctrl_c() => { tracing::info!("shutting down"); break; }
        }
        if source.is_finished() {
            break;
        }
    }
    stdout.flush().await?;
    report(&pipe, &rpc, true);
    if source.is_finished() {
        if let Ok(Err(e)) = source.await {
            bail!("source stopped: {}", redact::scrub(&e.to_string(), rpc.secrets()));
        }
    }
    Ok(())
}
