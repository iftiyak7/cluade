# Build Status

Only items marked **Done** are implemented *and* covered by passing tests in
this repository. Nothing here has been run against Solana mainnet yet.

Verify locally:

```bash
cargo test --workspace          # 52 tests
cargo clippy --workspace --all-targets -- -D warnings
scripts/test-migrations.sh      # applies schema to a throwaway Postgres + invariant checks
docker compose --env-file .env.example config -q
```

| # | Building | Status | Notes |
|---|---|---|---|
| 01 | Foundation | **Done** (architecture, schema, contracts, workspace) | `docs/architecture/ARCHITECTURE.md`, `database/migrations/0001_init.sql`, `packages/event-contracts` |
| 02 | 24/7 VPS operations | Partial | Compose for Postgres/Redis with health checks + log rotation. Service supervision, failover, backups: not started |
| 03 | Token discovery | Not started | Needs Geyser/RPC endpoint (operator-supplied) and current Pump.fun IDL |
| 04 | Token intelligence | **Core done** | Scoring + hard rejects + explanations (`pf-intel`). On-chain fact collection (holders, clusters, creator history) not started |
| 05 | Buy engine | Partial | Curve quoting / slippage / impact math (`pf-solana::curve`). Tx building, signing, submission, confirmation: not started |
| 06 | Hold & sell engine | **Core done** | Lifecycle state machine, TP ladder, trailing stop, smart hold, mandatory exits, reconciliation (`pf-position`) |
| 07 | Capital protection | **Core done** | Limits, clamping, breakers, profit target, restart persistence (`pf-risk`). DB persistence wiring not started |
| 08 | Wallet security | Not started | Design in ARCHITECTURE.md §1–2 |
| 09 | Admin panel | Not started | Schema for users/sessions/config versions/audit log exists |
| 10 | Analytics | Partial | Paper-trading simulation harness (`pf-sim`); no historical backtester yet |
| 11 | Telegram | Not started | |
| 12 | Testing & deployment | Partial | Unit + simulation tests, migration tests, CI |

## Verified behaviours (by test)

* Default config is paper mode, live off, master and auto-buy off; a buy is denied under defaults.
* Live orders need `mode=live` **and** `live_trading_enabled`.
* Duplicate intent ids are rejected by the risk engine and by the DB (`orders UNIQUE(intent_id, side)`).
* Risk state round-trips through JSON: daily counters, reservations, tripped breakers survive a restart.
* Daily loss / combined loss / drawdown / consecutive-loss / execution-failure breakers block **buys** but never block exits.
* Wallet reserve is never spent; sizes are clamped down by per-trade, per-token, total exposure, risk class.
* Hard stop-loss fires even with auto-sell, TP, trailing and momentum switches all off.
* Smart hold never engages on a losing position and never overrides max hold time.
* Urgent exits stack on top of a pending partial sell without over-selling.
* On-chain balance mismatch → `RECOVERY_REQUIRED`; no automatic action until an operator resolves it.
* Simulation: wallet delta equals realised PnL to the lamport; portfolio limits hold across 12 concurrent tokens; tokens with freeze authority are never bought.

## Known limitation demonstrated by tests

In the rug simulation the hard stop (-20%) filled at **-39%** because the
price gapped through it in a single step. Stop-losses do not cap losses on
gaps; position sizing and the TP ladder are what bound the damage.
