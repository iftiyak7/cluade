-- 0001_init.sql — foundation schema
-- Conventions:
--   * money in lamports / token base units as BIGINT (u64 fits in NUMERIC(20,0); we use
--     NUMERIC(20,0) where values may exceed i64, BIGINT otherwise)
--   * ratios in basis points (INTEGER)
--   * every table has exactly one writing service (see docs/architecture/ARCHITECTURE.md §3)

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------- enums
CREATE TYPE trading_mode      AS ENUM ('paper', 'live');
CREATE TYPE order_side        AS ENUM ('buy', 'sell');
CREATE TYPE order_status      AS ENUM ('created', 'submitted', 'confirmed', 'failed', 'expired');
CREATE TYPE position_state    AS ENUM ('ENTRY_PENDING','OPEN','HOLDING','PARTIAL_EXIT','EXIT_PENDING','CLOSED','RECOVERY_REQUIRED');
CREATE TYPE risk_verdict      AS ENUM ('approved', 'denied');
CREATE TYPE admin_role        AS ENUM ('owner', 'operator', 'viewer');

-- ---------------------------------------------------------------- discovery (writer: token-discovery)
CREATE TABLE tokens (
    mint              TEXT PRIMARY KEY,
    symbol            TEXT,
    name              TEXT,
    uri               TEXT,
    creator           TEXT NOT NULL,
    bonding_curve     TEXT NOT NULL,
    created_slot      BIGINT NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL,
    discovered_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    discovery_latency_us BIGINT,
    curve_complete    BOOLEAN NOT NULL DEFAULT false,
    migrated_at       TIMESTAMPTZ
);
CREATE INDEX tokens_creator_idx ON tokens (creator);
CREATE INDEX tokens_created_at_idx ON tokens (created_at DESC);

CREATE TABLE token_trades (
    signature         TEXT NOT NULL,
    ix_index          INTEGER NOT NULL,
    mint              TEXT NOT NULL REFERENCES tokens(mint),
    trader            TEXT NOT NULL,
    side              order_side NOT NULL,
    sol_amount        BIGINT NOT NULL CHECK (sol_amount >= 0),
    token_amount      NUMERIC(20,0) NOT NULL CHECK (token_amount >= 0),
    virtual_sol_reserves   NUMERIC(20,0) NOT NULL,
    virtual_token_reserves NUMERIC(20,0) NOT NULL,
    slot              BIGINT NOT NULL,
    block_time        TIMESTAMPTZ,
    PRIMARY KEY (signature, ix_index)
);
CREATE INDEX token_trades_mint_slot_idx ON token_trades (mint, slot DESC);

-- ---------------------------------------------------------------- blacklists (writer: admin-api)
CREATE TABLE blacklist (
    kind      TEXT NOT NULL CHECK (kind IN ('mint','creator','wallet')),
    address   TEXT NOT NULL,
    reason    TEXT,
    added_by  UUID,
    added_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, address)
);

-- ---------------------------------------------------------------- intelligence (writer: token-intelligence)
CREATE TABLE token_scores (
    id                BIGSERIAL PRIMARY KEY,
    mint              TEXT NOT NULL REFERENCES tokens(mint),
    scored_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    safety_score      SMALLINT NOT NULL CHECK (safety_score BETWEEN 0 AND 100),
    momentum_score    SMALLINT NOT NULL CHECK (momentum_score BETWEEN 0 AND 100),
    liquidity_score   SMALLINT NOT NULL CHECK (liquidity_score BETWEEN 0 AND 100),
    opportunity_score SMALLINT NOT NULL CHECK (opportunity_score BETWEEN 0 AND 100),
    hard_rejects      TEXT[] NOT NULL DEFAULT '{}',
    factors           JSONB NOT NULL            -- per-factor contributions for explainability
);
CREATE INDEX token_scores_mint_idx ON token_scores (mint, scored_at DESC);

-- ---------------------------------------------------------------- strategy (writer: strategy-engine)
CREATE TABLE entry_intents (
    intent_id         UUID PRIMARY KEY,
    mint              TEXT NOT NULL REFERENCES tokens(mint),
    strategy          TEXT NOT NULL,
    buy_mode          TEXT NOT NULL,
    mode              trading_mode NOT NULL,
    size_lamports     BIGINT NOT NULL CHECK (size_lamports > 0),
    max_slippage_bps  INTEGER NOT NULL CHECK (max_slippage_bps BETWEEN 0 AND 10000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL
);

-- ---------------------------------------------------------------- risk (writer: risk-engine)
CREATE TABLE risk_decisions (
    id                BIGSERIAL PRIMARY KEY,
    intent_id         UUID NOT NULL,
    side              order_side NOT NULL,
    verdict           risk_verdict NOT NULL,
    approved_lamports BIGINT,
    reasons           TEXT[] NOT NULL DEFAULT '{}',
    decided_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (intent_id, side)
);

-- Persisted so restarts never reset daily limits.
CREATE TABLE risk_daily_state (
    trading_day          DATE NOT NULL,
    mode                 trading_mode NOT NULL,
    realized_pnl_lamports BIGINT NOT NULL DEFAULT 0,
    fees_lamports        BIGINT NOT NULL DEFAULT 0,
    buys_count           INTEGER NOT NULL DEFAULT 0,
    consecutive_losses   INTEGER NOT NULL DEFAULT 0,
    equity_peak_lamports BIGINT NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (trading_day, mode)
);

CREATE TABLE circuit_breakers (
    breaker     TEXT NOT NULL,
    mode        trading_mode NOT NULL,
    tripped     BOOLEAN NOT NULL,
    reason      TEXT,
    tripped_at  TIMESTAMPTZ,
    reset_at    TIMESTAMPTZ,
    reset_by    UUID,
    PRIMARY KEY (breaker, mode)
);

-- ---------------------------------------------------------------- execution (writer: buy/sell executors)
CREATE TABLE orders (
    order_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id         UUID NOT NULL,
    side              order_side NOT NULL,
    mode              trading_mode NOT NULL,
    mint              TEXT NOT NULL REFERENCES tokens(mint),
    position_id       UUID,
    requested_lamports BIGINT,                      -- buys
    requested_tokens  NUMERIC(20,0),                -- sells
    min_out           NUMERIC(20,0) NOT NULL,
    status            order_status NOT NULL DEFAULT 'created',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    failure_reason    TEXT,
    -- idempotency: one order per intent per side, ever.
    UNIQUE (intent_id, side)
);

CREATE TABLE transactions (
    signature         TEXT PRIMARY KEY,
    order_id          UUID NOT NULL REFERENCES orders(order_id),
    attempt           SMALLINT NOT NULL,
    rpc_provider      TEXT NOT NULL,
    priority_fee_micro_lamports BIGINT NOT NULL,
    recent_blockhash  TEXT NOT NULL,
    last_valid_block_height BIGINT NOT NULL,
    submitted_at      TIMESTAMPTZ NOT NULL,
    confirmed_slot    BIGINT,
    confirmed_at      TIMESTAMPTZ,
    err               TEXT,
    -- actual, observed results (never assumed from the request)
    sol_delta_lamports BIGINT,                      -- negative for buys
    token_delta       NUMERIC(20,0),
    network_fee_lamports BIGINT,
    -- latency instrumentation (microseconds)
    build_us          BIGINT,
    sign_us           BIGINT,
    submit_us         BIGINT,
    confirm_us        BIGINT
);
CREATE INDEX transactions_order_idx ON transactions (order_id);

-- ---------------------------------------------------------------- positions (writer: position-manager)
CREATE TABLE positions (
    position_id       UUID PRIMARY KEY,
    mint              TEXT NOT NULL REFERENCES tokens(mint),
    mode              trading_mode NOT NULL,
    strategy          TEXT NOT NULL,
    state             position_state NOT NULL,
    entry_intent_id   UUID NOT NULL UNIQUE,
    cost_lamports     BIGINT NOT NULL DEFAULT 0,      -- total SOL spent incl. fees
    tokens_held       NUMERIC(20,0) NOT NULL DEFAULT 0,
    tokens_bought     NUMERIC(20,0) NOT NULL DEFAULT 0,
    proceeds_lamports BIGINT NOT NULL DEFAULT 0,      -- SOL received from sells net of fees
    peak_value_lamports BIGINT NOT NULL DEFAULT 0,
    tp_levels_hit     SMALLINT NOT NULL DEFAULT 0,
    opened_at         TIMESTAMPTZ,
    closed_at         TIMESTAMPTZ,
    exit_reason       TEXT,
    version           INTEGER NOT NULL DEFAULT 0      -- optimistic concurrency
);
CREATE INDEX positions_open_idx ON positions (mode, state) WHERE state <> 'CLOSED';

CREATE TABLE position_events (
    id           BIGSERIAL PRIMARY KEY,
    position_id  UUID NOT NULL REFERENCES positions(position_id),
    from_state   position_state,
    to_state     position_state NOT NULL,
    reason       TEXT NOT NULL,
    data         JSONB,
    at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- admin (writer: admin-api)
CREATE TABLE admin_users (
    user_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username       TEXT NOT NULL UNIQUE,
    password_hash  TEXT NOT NULL,                     -- argon2id
    totp_secret_enc BYTEA,                            -- encrypted, never plaintext
    role           admin_role NOT NULL DEFAULT 'viewer',
    mfa_enrolled   BOOLEAN NOT NULL DEFAULT false,
    failed_logins  INTEGER NOT NULL DEFAULT 0,
    locked_until   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE admin_sessions (
    session_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID NOT NULL REFERENCES admin_users(user_id),
    token_hash     TEXT NOT NULL UNIQUE,
    ip             INET,
    user_agent     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    reauth_at      TIMESTAMPTZ,                       -- last MFA re-auth, for sensitive ops
    expires_at     TIMESTAMPTZ NOT NULL,
    revoked_at     TIMESTAMPTZ
);

CREATE TABLE config_versions (
    version        SERIAL PRIMARY KEY,
    config         JSONB NOT NULL,
    changed_by     UUID REFERENCES admin_users(user_id),
    change_note    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    active         BOOLEAN NOT NULL DEFAULT false
);
CREATE UNIQUE INDEX config_one_active ON config_versions (active) WHERE active;

-- Append-only, hash-chained audit log.
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor       UUID,
    actor_kind  TEXT NOT NULL CHECK (actor_kind IN ('admin','system','telegram')),
    action      TEXT NOT NULL,
    target      TEXT,
    details     JSONB,
    ip          INET,
    prev_hash   BYTEA,
    hash        BYTEA NOT NULL
);

CREATE FUNCTION audit_log_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END $$;
CREATE TRIGGER audit_log_no_update BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_immutable();

CREATE TABLE operator_commands (
    command_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issued_by    UUID NOT NULL REFERENCES admin_users(user_id),
    command      TEXT NOT NULL,
    args         JSONB,
    issued_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    acked_at     TIMESTAMPTZ,
    result       TEXT
);

-- ---------------------------------------------------------------- notifications (writer: notifications)
CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    channel     TEXT NOT NULL,
    kind        TEXT NOT NULL,
    payload     JSONB NOT NULL,
    event_id    UUID UNIQUE,                         -- dedupe at-least-once events
    sent_at     TIMESTAMPTZ,
    error       TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------- outbox (writer: each publisher, own rows)
CREATE TABLE event_outbox (
    event_id     UUID PRIMARY KEY,
    stream       TEXT NOT NULL,
    payload      JSONB NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX event_outbox_unpublished ON event_outbox (created_at) WHERE published_at IS NULL;

COMMIT;
