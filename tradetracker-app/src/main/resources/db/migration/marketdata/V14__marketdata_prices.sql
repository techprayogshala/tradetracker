-- =============================================================================
-- V1__marketdata_prices.sql
-- Price timeseries using TimescaleDB hypertable + FX rates + corporate actions
-- =============================================================================

-- ── Daily OHLCV prices ───────────────────────────────────────────────────────
CREATE TABLE security_prices (
    security_id     UUID          NOT NULL REFERENCES securities(id),
    price_date      DATE          NOT NULL,
    open            NUMERIC(20,8) NOT NULL,
    high            NUMERIC(20,8) NOT NULL,
    low             NUMERIC(20,8) NOT NULL,
    close           NUMERIC(20,8) NOT NULL,
    adjusted_close  NUMERIC(20,8) NOT NULL,     -- Split/dividend adjusted
    volume          BIGINT,
    source          VARCHAR(50)   NOT NULL DEFAULT 'ALPHA_VANTAGE',
    PRIMARY KEY (security_id, price_date)
);

-- Convert to TimescaleDB hypertable — automatic time-based partitioning.
-- This gives sub-second range queries over years of daily price data.
SELECT create_hypertable('security_prices', 'price_date',
    chunk_time_interval => INTERVAL '3 months',
    if_not_exists => TRUE
);

CREATE INDEX idx_prices_security_date
    ON security_prices (security_id, price_date DESC);

-- ── FX rates (daily, relative to USD as base) ────────────────────────────────
CREATE TABLE fx_rates (
    rate_date       DATE          NOT NULL,
    from_currency   CHAR(3)       NOT NULL,
    to_currency     CHAR(3)       NOT NULL,
    rate            NUMERIC(20,8) NOT NULL,
    source          VARCHAR(50)   NOT NULL DEFAULT 'OPEN_EXCHANGE_RATES',
    PRIMARY KEY (rate_date, from_currency, to_currency)
);

SELECT create_hypertable('fx_rates', 'rate_date',
    chunk_time_interval => INTERVAL '1 year',
    if_not_exists => TRUE
);

CREATE INDEX idx_fx_pair_date ON fx_rates (from_currency, to_currency, rate_date DESC);

-- ── Corporate actions ────────────────────────────────────────────────────────
CREATE TABLE corporate_actions (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    security_id     UUID         NOT NULL REFERENCES securities(id),
    action_type     VARCHAR(50)  NOT NULL,
    -- STOCK_SPLIT | MERGER | SPIN_OFF | RIGHTS_ISSUE | RETURN_OF_CAPITAL
    effective_date  DATE         NOT NULL,
    payload         JSONB        NOT NULL,   -- Action-specific data (ratio, etc.)
    applied_at      TIMESTAMPTZ,             -- NULL = pending
    applied_by      UUID         REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_corp_actions_security ON corporate_actions (security_id, effective_date DESC);
CREATE INDEX idx_corp_actions_pending  ON corporate_actions (applied_at)
    WHERE applied_at IS NULL;

-- ── Price data sync log ──────────────────────────────────────────────────────
CREATE TABLE price_sync_log (
    id              BIGSERIAL    PRIMARY KEY,
    security_id     UUID         REFERENCES securities(id),
    sync_type       VARCHAR(50)  NOT NULL,   -- DAILY | HISTORICAL | FX_RATES
    started_at      TIMESTAMPTZ  NOT NULL,
    completed_at    TIMESTAMPTZ,
    records_upserted INT,
    error_message   TEXT,
    source          VARCHAR(50)
);
