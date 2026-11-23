# Runbook — paper trading

Paper mode is the only mode `pf-trader` supports. It reads live mainnet
data, models fills, and never signs or sends a transaction.

## Start

```bash
docker compose up -d postgres            # or any Postgres 16
set -a; . ./.env; set +a                 # RPC_* , RPC_MAX_RPS
export DATABASE_URL="host=127.0.0.1 port=5432 user=pumpfun password=$POSTGRES_PASSWORD dbname=pumpfun"
./target/release/pf-trader --source blocks --config config/paper.json \
  --record data/rec-$(date +%F).jsonl --mint-cache data/mints.jsonl \
  --journal data/journal.jsonl --report data/report.json
```

Migrations apply automatically on start. `PROGRESS {...}` lines (stderr,
every 60 s) carry the full report: wallet, open positions, closed-trade
metrics, breakers, decision counters, discovery lag and RPC health.

## Restart / crash recovery

Just start it again (without `--fresh`). The engine restores wallet, risk
state (daily counters, breakers), open positions and closed trades from
`engine_snapshots`. In-flight paper orders are reconciled conservatively:
pending entries are cancelled and their risk reservation released; pending
exits are retried. Under systemd/Docker use `restart: unless-stopped`.

## Inspect

```sql
-- open and recent positions
SELECT mint, state, cost_lamports/1e9 AS cost_sol, proceeds_lamports/1e9 AS proceeds_sol, exit_reason, opened_at, closed_at
FROM positions WHERE mode = 'paper' ORDER BY opened_at DESC LIMIT 20;

-- why buys were denied by the risk engine
SELECT unnest(reasons) AS reason, count(*) FROM risk_decisions WHERE verdict = 'denied' GROUP BY 1 ORDER BY 2 DESC;

-- what drags safety scores down
WITH f AS (SELECT jsonb_array_elements(factors) x FROM token_scores WHERE scored_at > now() - interval '1 hour')
SELECT x->>'name', count(*), round(avg((x->>'contribution')::int), 1) FROM f WHERE x->>'score' = 'safety' GROUP BY 1 ORDER BY 3;

-- paper fills with costs
SELECT o.side, o.status, f.sol_delta/1e9 AS sol, f.curve_fees, f.network_fee, f.price_impact_bps
FROM paper_fills f JOIN orders o USING (order_id) ORDER BY f.at DESC LIMIT 20;
```

## Compare strategies on recorded data

```bash
pf-trader --source replay --replay data/rec-2026-11-23.jsonl --mint-cache data/mints.jsonl --config config/paper.json --report a.json
pf-trader --source replay --replay data/rec-2026-11-23.jsonl --mint-cache data/mints.jsonl --config config/research-relaxed.json --report b.json
```

Replays are deterministic (same input → identical report). They are
in-sample: tuning on one recording and testing on the same recording
overstates results. Use a later recording for out-of-sample checks.

## Emergency

Stop the process (Ctrl-C / `docker stop`): a final snapshot is written.
Nothing is at risk in paper mode; no wallet key is loaded.
