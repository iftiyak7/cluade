# Build Status

Only items marked **Done** are implemented *and* covered by passing tests in
this repository. Read-only discovery has been run against Solana mainnet
(see `MAINNET_FINDINGS_2026-11-21.md`) and a 15-minute live paper-trading
session has been run (`MAINNET_FINDINGS_2026-11-23.md`); **no transaction has
ever been signed or sent**.

Verify locally:

```bash
TEST_DATABASE_URL=... cargo test --workspace   # 108 tests (real mainnet fixtures + replay, Postgres integration)
cargo clippy --workspace --all-targets -- -D warnings
scripts/test-migrations.sh      # applies schema to a throwaway Postgres + invariant checks
docker compose --env-file .env.example config -q
```

| # | Building | Status | Notes |
|---|---|---|---|
| 01 | Foundation | **Done** (architecture, schema, contracts, workspace) | `docs/architecture/ARCHITECTURE.md`, `database/migrations/0001_init.sql`, `packages/event-contracts` |
| 02 | 24/7 VPS operations | Partial | Compose for Postgres/Redis; RPC endpoint pool with health scoring, failover and pacing; **restart recovery from engine snapshots (verified)**. Supervision units, backups, alerting: not started |
| 03 | Token discovery | **Partial — live-verified** | Decoder for current program (forward-compatible, logs + emit_cpi), exact curve math reproduced on 10 mainnet trades, `pf-discovery` service: block source run live on mainnet (0 decode errors, 0 rate-limit errors at 5 req/s, ~3.4 s lag); WS source unit-tested only; Geyser, persistence and Redis publishing not started |
| 04 | Token intelligence | **Done for paper** | Scoring with explanations; live inputs from `pf-market` (holders/concentration from net curve flow, bundled-launch share, wash share, creator sold, in-session creator history); **mint inspection** (authorities, Token-2022 extensions, verified on real mints). Not measured: wallet-cluster funding graph, transfers outside the curve, cross-session creator history |
| 05 | Buy engine | **Paper done** / live not started | Buy modes (sniper, smart entry, conservative, momentum confirmation, manual) in `pf-strategy`; paper fills with landing latency and slippage failures; exact curve quoting. Live tx building/signing/submission: not started |
| 06 | Hold & sell engine | **Done for paper** | Lifecycle, TP ladder, trailing stop, smart hold, mandatory exits, creator-dump exit, failed-exit escalation to urgent, migration handling |
| 07 | Capital protection | **Done for paper** | Risk engine is final authority on every paper order; state persisted in snapshots and restored on restart |
| 08 | Wallet security | Not started | Design in ARCHITECTURE.md §1–2 |
| 09 | Admin panel | Not started | Schema for users/sessions/config versions/audit log exists |
| 10 | Analytics | Partial | `pf-analytics` metrics; **deterministic replay of recorded mainnet sessions** for strategy comparison. Walk-forward tooling, dashboards: not started |
| 11 | Telegram | Not started | |
| 12 | Testing & deployment | Partial | Unit, mainnet-fixture, replay, Postgres integration, migration tests; CI with Postgres service. Load/E2E/deployment scripts: not started |

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

## Mainnet-verified behaviours

* Event decoding matches an independent IDL-driven decoder on 9 real transactions (create, buy, sell, routed trades, non-SOL quote curves, curve completion, AMM migration).
* Curve math reproduces every captured trade exactly: tokens, quote amount, fees and post-trade reserves.
* Truncated logs lose events (a first buy; a migration); the inner-instruction path recovers them.
* API key never appears in service output (checked on all live runs).

## Known limitation demonstrated by tests

In the rug simulation the hard stop (-20%) filled at **-39%** because the
price gapped through it in a single step. Stop-losses do not cap losses on
gaps; position sizing and the TP ladder are what bound the damage.
