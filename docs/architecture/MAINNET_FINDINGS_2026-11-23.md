# Mainnet findings — 2026-11-23 (first live paper-trading session)

15-minute live paper session (`pf-trader --source blocks --config config/paper.json`),
Helius RPC at 5 req/s, Postgres persistence on. No transaction was signed or sent.

## Outcome

| Metric | Value |
|---|---|
| New tokens discovered | 806 (≈ 54/min) |
| Trades tracked (tokens whose creation we saw) | 41,199 |
| Mint accounts inspected | 242 — all clean (no authorities, metadata extensions only) |
| Entry proposals from strategy | **4** |
| Approved by risk engine | **0** — all denied: *market data stale (14.5–18.1 s)* |
| Paper trades | 0 · wallet unchanged · accounting check 0 |

## Why so few candidates

| Filter | Count |
|---|---|
| Rejected: mayhem-mode curve | 215 |
| Rejected: quote asset not SOL (`pumpCmXq…` alone: 52) | ~125 |
| Rejected: creator dumped an earlier launch within 5 min this session | 27 |
| Waiting, most common reasons | safety < 70 (648), top-10 concentration > 30% (347), mint not yet inspected (810), too few trades (167) |

Safety deductions (from `token_scores.factors`): bundled launches (same-slot
buys) were the most frequent penalty; serial-dumper creators the largest.

## The real bottleneck: feed latency under load

Discovery lag in this session was **p50 17 slots / ~10 s** (p95 44 slots / ~16 s),
versus 5 slots / 3.4 s in the 90-second test two days earlier. Block
fetching, tip polling and mint inspection share one 5 req/s budget, so the
mint-inspection queue backs up and the block follower falls behind. The risk
engine correctly refused to trade on data that old.

**Conclusion:** strategy and safety logic work end to end, but the current
RPC plan cannot support entries. A streaming feed (Yellowstone Geyser or an
enhanced WebSocket on a higher Helius tier) is required before paper results
mean anything, let alone live trading.

## Replay comparison (same 60,841 recorded envelopes, deterministic)

| Config | Proposals | Trades | Net |
|---|---|---|---|
| `paper.json` (defaults) | 4 | 0 (stale data) | 0 |
| `research-relaxed.json` (looser concentration/safety) | 5 | 1 | −0.0054 SOL (hard stop after 25 s) |

One trade is not evidence of anything; it is reported for completeness.
Relaxing filters added exposure to exactly the tokens the defaults avoid.

## Restart recovery (verified)

Restarting `pf-trader` against the same database restored the engine from
`engine_snapshots` (counters continued 806 → 840, wallet preserved,
migrations not re-applied).
