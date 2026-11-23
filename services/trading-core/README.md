# services/trading-core (`pf-trader`)

The decision pipeline in one process (ARCHITECTURE.md §2):

```
pf-discovery source ─▶ pipeline ─▶ Engine.handle(envelope)
                                     │  MarketBook (pf-market)      per-token curve, windows, holders
                                     │  mint inspection (pf-solana::token) — fetched once per candidate
                                     │  scoring (pf-intel) → entry decision (pf-strategy)
                                     │  RiskState (pf-risk)         final authority, sizing
                                     │  paper broker (paper.rs)     landing latency + slippage failures
                                     │  Position (pf-position)      lifecycle, TP / trailing / stops
                                     └─▶ effects: FetchMint · Db(DbOp) · Log
```

The engine is a pure state machine (no I/O, time passed in), so live runs
and replays execute identical code. **Paper only**: `Engine::new` refuses
any configuration with `mode = live`; live executors and the wallet signer
do not exist yet.

## Run

```bash
set -a; . ./.env; set +a
export DATABASE_URL="host=127.0.0.1 port=5432 user=pumpfun password=… dbname=pumpfun"   # optional
cargo run --release -p pf-trading-core --bin pf-trader -- \
  --source blocks --config config/paper.json \
  --record rec.jsonl --mint-cache mints.jsonl --journal journal.jsonl --report report.json
```

* With `DATABASE_URL`: migrations are applied on start, every token, trade,
  score, intent, risk decision, order, fill and position is written, and an
  engine snapshot is saved every 10 s and on every close. Restarting resumes
  from the snapshot (`--fresh` ignores it). In-flight paper orders do not
  survive a restart: pending entries are cancelled (risk reservation
  released) and pending exits are retried.
* `PROGRESS {...}` every 60 s and `REPORT {...}` at exit on stderr.

## Replay (deterministic)

```bash
pf-trader --source replay --replay rec.jsonl --mint-cache mints.jsonl --config config/paper.json --report replay.json
```

Mint accounts come from the cache recorded live; a mint missing from the
cache is never bought.

## Paper fill model (pessimistic by design)

| Aspect | Model |
|---|---|
| Landing | decision at chain tip `T` lands at `T + landing_slots` (default 2); priced at the first observed state at/after that slot |
| Slippage | fill fails if output < quote × (1 − max_slippage); failed txs still pay `network_fee` |
| Costs | per-curve protocol + creator fees (exact chain rounding), `network_fee` per tx (default 0.000105 SOL), token-account rent 0.00207408 SOL locked until close |
| Exit retries | a failed exit is retried as *urgent* (2× slippage, risk-capped at 50%) |
| Migration | curve completion while holding → closed at the final curve price, labelled `MigratedApproximated` (AMM exit not modelled) |
| Known optimism | our fills do not move the real curve afterwards |

## Accounting invariant

`report.accounting_check_lamports` must be **0**:
`wallet = start + realised PnL − failed-tx fees + (proceeds − cost of open positions) − rent locked`.
It is checked in every engine test and every live run.
