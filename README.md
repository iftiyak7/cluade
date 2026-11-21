# Pump.fun Trading Platform

An autonomous Solana / Pump.fun trading platform built in 12 stages
("buildings"). This repository currently contains the **foundation and the
core decision logic**, not a live-trading bot. See
[`docs/architecture/BUILD_STATUS.md`](docs/architecture/BUILD_STATUS.md) for
exactly what is implemented and tested.

> **Risk warning.** Trading newly launched Pump.fun tokens is extremely
> high-risk. Most such tokens go to zero, rugs are common, and stop-losses
> can fill far below their trigger. Nothing in this project predicts profit.
> Live trading is disabled by default; use a dedicated wallet funded only
> with money you can afford to lose.

## Layout

| Path | Purpose |
|---|---|
| `docs/architecture/` | System design, service boundaries, event flow, failure handling |
| `database/migrations/` | Postgres schema (single source of truth) |
| `packages/shared-types` (`pf-types`) | Integer money types, bps, enums |
| `packages/configuration` (`pf-config`) | Validated runtime config; safe defaults |
| `packages/event-contracts` (`pf-events`) | Versioned event envelope + payloads |
| `packages/solana-adapters` (`pf-solana`) | Bonding-curve math (RPC/Geyser later) |
| `services/risk-engine` (`pf-risk`) | Final authority on every order |
| `services/position-manager` (`pf-position`) | Lifecycle + exit logic |
| `services/token-intelligence` (`pf-intel`) | Safety / momentum / liquidity scoring |
| `tests/simulation` (`pf-sim`) | Paper-trading integration harness |
| `apps/`, other `services/` | Placeholders with documented responsibilities |

## Quick start (development)

```bash
cargo test --workspace
scripts/test-migrations.sh
cp .env.example .env   # edit passwords
docker compose up -d   # postgres + redis on 127.0.0.1 only
```

## Before any live trading

1. All automated tests green.
2. Verified on-chain transaction handling (devnet / tiny mainnet test).
3. Paper trading over a meaningful period.
4. Risk controls validated; wallet security reviewed.
5. Small, explicitly authorised live test; accounting reconciled.
6. Administrator approval (MFA) to enable autonomous trading.

## External dependencies you will need

* Dedicated Solana RPC (HTTP+WS), ideally two providers.
* Yellowstone Geyser gRPC endpoint for low-latency discovery.
* Optional transaction-delivery service (e.g. Jito) credentials.
* Telegram bot token.
* An Ubuntu LTS VPS (≥ 4 vCPU / 8 GB suggested), close to your RPC region.
