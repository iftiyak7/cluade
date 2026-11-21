# System Architecture — Pump.fun Autonomous Trading Platform

> Status: **Building 01 (Foundation)**. This document is the contract every
> later building must conform to. Changes to service boundaries or event
> contracts require updating this file first.

## 1. Design principles

1. **Risk engine has final authority.** No order reaches the signer without a
   `RiskApproved` decision bound to that exact intent id, size and expiry.
2. **Defence in depth on money.** The wallet signer enforces its *own* hard
   caps (per-tx, per-day, instruction allowlist) so that a bug in the
   strategy or risk engine cannot drain the wallet.
3. **Integer money only.** All amounts are `u64` lamports / token base units;
   ratios are basis points (1 bp = 0.01%). Floats are allowed only for
   *scores* and analytics display, never for balances or limits.
4. **Idempotency everywhere.** Every intent/order carries a UUID that is a
   unique key in Postgres. Retries and restarts cannot create a second buy.
5. **Submission ≠ success.** A position becomes `OPEN` only after the token
   balance is observed on-chain at the required commitment.
6. **Live trading off by default.** `LIVE_TRADING_ENABLED=false` and
   `mode=paper` in the default configuration; enabling live requires an
   MFA-reauthenticated admin action recorded in the audit log.
7. **Few processes, clear modules.** Latency-critical logic (discovery →
   intelligence → strategy → risk → execution) runs in **one Rust process**
   (`trading-core`) as separate crates/tasks connected by in-process channels.
   Out-of-process boundaries exist only where isolation is worth the cost:
   the signer, the admin API, notifications and analytics.

## 2. Runtime topology

```
                 ┌───────────────────────────── VPS (Docker Compose) ─────────────────────────────┐
 Internet ─TLS─▶ │ nginx ──▶ admin-dashboard (Next.js) ──▶ admin-api (NestJS) ──gRPC──┐           │
                 │                                             │  │                  │           │
                 │                                   Postgres ◀┘  └▶ Redis Streams   │           │
                 │                                       ▲              ▲  │          ▼           │
                 │  ┌──────────────── trading-core (Rust, single process) ─────────────────┐     │
 Geyser/RPC ───▶ │  │ token-discovery → market-data → token-intelligence → strategy-engine │     │
 (primary+       │  │        │                                              │             │     │
  secondary)     │  │        └────────────▶ position-manager ◀── risk-engine ◀┘            │     │
                 │  │                            │   ▲              │ (approve/deny)        │     │
                 │  │                 sell-executor  buy-executor ◀─┘                      │     │
                 │  └────────────────────────────┬──────────────┬───────────────────────────┘     │
                 │                       gRPC (unix socket, internal network only)                │
                 │                               ▼                                                │
                 │                       wallet-signer (Rust, no egress, key encrypted at rest)   │
                 │  notifications (Telegram, outbound only)   analytics (batch/readonly replica)  │
                 └────────────────────────────────────────────────────────────────────────────────┘
```

Only nginx (443) and SSH (key-only) are reachable from the internet.
Postgres, Redis, the signer and gRPC endpoints are bound to the internal
Docker network.

## 3. Service boundaries

| Module | Process | Owns (writes) | Consumes | Emits |
|---|---|---|---|---|
| token-discovery | trading-core | `tokens`, `token_events` | Geyser/RPC | `TokenDiscovered`, `TradeObserved`, `CurveCompleted` |
| market-data | trading-core | `market_snapshots` | `TradeObserved` | `MarketSnapshot` |
| token-intelligence | trading-core | `token_scores` | `TokenDiscovered`, `MarketSnapshot` | `TokenScored` |
| strategy-engine | trading-core | `entry_intents` | `TokenScored`, `MarketSnapshot`, config | `EntryIntent` |
| risk-engine | trading-core | `risk_decisions`, `risk_state`, `circuit_breakers` | `EntryIntent`, `ExitIntent`, fills, health | `RiskDecision`, `CircuitBreakerTripped` |
| buy-executor | trading-core | `orders`, `transactions` | `RiskDecision(approved, buy)` | `OrderSubmitted`, `OrderFilled`, `OrderFailed` |
| sell-executor | trading-core | `orders`, `transactions` | `RiskDecision(approved, sell)` | same |
| position-manager | trading-core | `positions`, `position_events` | fills, `MarketSnapshot` | `ExitIntent`, `PositionStateChanged` |
| wallet-signer | separate | `signer_ledger` (own DB schema) | gRPC `SignRequest` | signed tx |
| admin-api | separate | `admin_users`, `sessions`, `config_versions`, `audit_log`, `operator_commands` | Redis streams (read) | `OperatorCommand`, `ConfigChanged` |
| notifications | separate | `notifications` | all events (read) | Telegram messages |
| analytics | separate | `analytics_*` (materialised) | Postgres (read replica / read-only role) | — |

Each table has exactly **one writer**. Other modules read via the owning
module's API/events or read-only DB roles.

## 4. Event transport

* In-process: bounded `tokio::mpsc` channels (back-pressure, no silent drops
  for orders; discovery/market events may be dropped under overload and are
  counted in metrics).
* Cross-process: Redis Streams (`pf:events:<type>`), consumer groups, at-least-
  once delivery. Consumers dedupe on `event_id`.
* Every event uses the versioned envelope in `packages/event-contracts`.
* Durable facts (orders, fills, positions, risk state, config, audit) are
  written to Postgres **before** the event is published (outbox pattern).

## 5. Order lifecycle (buy)

1. `strategy-engine` emits `EntryIntent{intent_id, mint, size_lamports, max_slippage_bps, expires_at}`.
2. `risk-engine` evaluates against current exposure, limits and breakers;
   persists `risk_decisions` and reserves exposure atomically. Denials are
   recorded with reasons.
3. `buy-executor` inserts `orders(intent_id UNIQUE)` → if conflict, stop
   (duplicate). Builds tx, asks signer, submits to RPC(s), stores signature.
4. Confirmation tracker polls/streams signature status and the ATA balance.
   On confirmed balance → `OrderFilled` with *actual* SOL spent and tokens
   received (including fees). On expiry/failure → `OrderFailed`, risk
   reservation released.
5. `position-manager` moves `ENTRY_PENDING → OPEN` only on `OrderFilled`.

## 6. Position lifecycle

```
ENTRY_PENDING ──fill──▶ OPEN ──smart-hold conditions──▶ HOLDING
     │                   │  ▲                              │
  fail/expire            │  └──────conditions lost─────────┘
     ▼                   ▼ partial TP                      │
  CLOSED            PARTIAL_EXIT ──────────────────────────┤
                         │                                 ▼
                         └─────────exit trigger──────▶ EXIT_PENDING ──fill(all)──▶ CLOSED
                                                           │
                                     unreconcilable state  ▼
                                                    RECOVERY_REQUIRED (operator)
```

Implemented in `services/position-manager` (`pf-position`).

## 7. Failure handling

| Failure | Detection | Response |
|---|---|---|
| RPC/Geyser disconnect | heartbeat/slot lag | reconnect w/ backoff; failover to secondary; breaker `DataStale` blocks new buys after threshold |
| Stale market data | snapshot age > limit | block new buys; exits still attempted using last-known + fresh quote |
| Tx not landing | no confirmation before blockhash expiry | mark failed, release reservation, count toward `ExecutionFailures` breaker; **never** blind-resubmit a buy without re-approval |
| Process crash | Docker restart policy + health check | on boot: load risk state + positions from Postgres, reconcile wallet balances vs positions; mismatches → `RECOVERY_REQUIRED`, new buys blocked |
| Postgres down | connection errors | trading-core enters safe mode (no new buys; exits allowed in-memory, persisted when DB returns) |
| Signer unavailable | gRPC health | no new orders; alert |
| Daily loss / drawdown | risk-engine | breaker trips, persisted, survives restart until next UTC day or manual reset by admin |

## 8. Latency budget (targets, not guarantees)

| Stage | Target P95 |
|---|---|
| Geyser event received → decoded | < 5 ms |
| Decoded → scored + strategy decision | < 20 ms |
| Risk decision | < 1 ms |
| Tx build + sign (signer RTT over UDS) | < 10 ms |
| Submission call | < 50 ms |
| Inclusion / confirmation | network-dependent; measured, not promised |

## 9. External dependencies (must be provided by operator)

* Dedicated Solana RPC (HTTP + WS) — paid provider recommended; primary + secondary.
* Yellowstone gRPC (Geyser) endpoint — optional but needed for sub-second discovery.
* Optional transaction delivery service (e.g. Jito bundles) — credentials required.
* Telegram bot token + chat id.
* A **dedicated** trading wallet funded with an amount you can afford to lose.

## 9a. Verified mainnet constraints

See `MAINNET_FINDINGS_2026-11-21.md`: non-SOL quote curves, per-curve fees,
Token-2022 mints, log truncation (~2%), ~350 program tx/s with ~80% failing,
and measured discovery latency per source. These override any assumption above.

## 10. Construction stages

See `docs/architecture/BUILD_STATUS.md` for verified progress per building.
