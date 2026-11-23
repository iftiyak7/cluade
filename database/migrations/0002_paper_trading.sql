-- 0002_paper_trading.sql — paper execution, richer discovery data, restart snapshots

BEGIN;

-- Discovery: quote asset and program details (non-SOL curves exist; Token-2022 mints).
ALTER TABLE tokens
    ADD COLUMN quote_mint    TEXT,
    ADD COLUMN token_program TEXT,
    ADD COLUMN mayhem        BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE token_trades
    ADD COLUMN quote_mint TEXT,
    ADD COLUMN fees       BIGINT,
    ADD COLUMN ix_name    TEXT;
COMMENT ON COLUMN token_trades.sol_amount IS 'Quote units moved through the curve (lamports when quote_mint is SOL)';
COMMENT ON COLUMN token_trades.virtual_sol_reserves IS 'Post-trade virtual quote reserves';

ALTER TABLE tokens ADD COLUMN completed_at TIMESTAMPTZ;

-- Exit intents are "<position>:exit:<n>", not UUIDs.
ALTER TABLE orders ALTER COLUMN intent_id TYPE TEXT;
ALTER TABLE risk_decisions ALTER COLUMN intent_id TYPE TEXT;

-- Paper fills (live fills go to `transactions`).
CREATE TABLE paper_fills (
    id               BIGSERIAL PRIMARY KEY,
    order_id         UUID NOT NULL REFERENCES orders(order_id),
    slot             BIGINT NOT NULL,
    at               TIMESTAMPTZ NOT NULL,
    sol_delta        BIGINT NOT NULL,
    token_delta      NUMERIC(20,0) NOT NULL,
    curve_fees       BIGINT NOT NULL,
    network_fee      BIGINT NOT NULL,
    price_impact_bps INTEGER NOT NULL
);
CREATE INDEX paper_fills_order_idx ON paper_fills (order_id);

-- Full position state for exact restore (TP levels hit, trailing state, history).
ALTER TABLE positions ADD COLUMN state_json JSONB;

-- One snapshot per mode: wallet, risk state, positions, closed trades.
CREATE TABLE engine_snapshots (
    mode       trading_mode PRIMARY KEY,
    snapshot   JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
