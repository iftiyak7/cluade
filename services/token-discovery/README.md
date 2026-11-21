# services/token-discovery (`pf-discovery`)

Real-time Pump.fun discovery (Building 03). Emits one JSON
[`Envelope`](../../packages/event-contracts/src/lib.rs) per line on stdout:
`TokenDiscovered`, `TradeObserved`, `CurveCompleted`. Periodic `STATS {...}`
JSON (counters, P50/P95/P99 latencies, per-endpoint RPC health) on stderr.

## Sources

| `--source` | Transport | Latency | Completeness | Verified |
|---|---|---|---|---|
| `blocks` | HTTP `getBlock` (confirmed) | ~3–4 s behind block time | complete (inner-instruction events) | **live on mainnet** |
| `ws` (default) | WebSocket `logsSubscribe` (processed) + HTTP backfill of truncated logs | expected sub-second | complete via backfill | unit-tested only (sandbox proxy blocks WebSockets) |

Geyser (Yellowstone gRPC) is the recommended production source and is not
implemented yet.

## Run

```bash
set -a; . ./.env; set +a            # RPC_PRIMARY_HTTP, RPC_SECONDARY_HTTP, RPC_*_WS
cargo run --release -p pf-discovery -- --source blocks --duration 60
cargo run --release -p pf-discovery -- --source ws --creates-only
```

Environment:

* `RPC_MAX_RPS` (default 5) — client-side pacing shared by all endpoints.
* `RUST_LOG` — log level (`info` default).

API keys in RPC URLs are redacted from all logs and errors (`src/redact.rs`).

## Not yet done

* Publishing to Redis Streams / writing `tokens` & `token_trades` (outbox).
* Geyser source; WS → block-source gap backfill wiring (gaps are reported, not yet filled).
* Mint-extension (Token-2022) inspection — belongs to token-intelligence.
