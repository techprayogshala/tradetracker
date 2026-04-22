-- =============================================================================
-- V1__broker_connections.sql
-- Stores encrypted OAuth tokens and sync state for broker integrations.
-- =============================================================================

CREATE TABLE broker_connections (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    portfolio_id    UUID         NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    broker          VARCHAR(50)  NOT NULL,   -- COMMSEC | SELFWEALTH | IBKR | STAKE | PEARLER
    display_name    VARCHAR(255),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | EXPIRED | REVOKED | ERROR
    -- Tokens stored encrypted — pgcrypto symmetric encryption key from env
    access_token_enc  TEXT,
    refresh_token_enc TEXT,
    token_expires_at  TIMESTAMPTZ,
    last_sync_at      TIMESTAMPTZ,
    last_sync_status  VARCHAR(20),           -- SUCCESS | PARTIAL | FAILED
    last_error        TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, broker, portfolio_id)
);

CREATE INDEX idx_broker_conn_user      ON broker_connections (user_id);
CREATE INDEX idx_broker_conn_portfolio ON broker_connections (portfolio_id);

-- Raw import log — every trade ingested from a broker before normalisation
CREATE TABLE broker_raw_imports (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    connection_id   UUID         NOT NULL REFERENCES broker_connections(id),
    broker          VARCHAR(50)  NOT NULL,
    raw_payload     JSONB        NOT NULL,   -- Original broker response, kept for audit
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | PROCESSED | DUPLICATE | ERROR
    trade_event_id  UUID         REFERENCES trade_events(id),
    imported_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ
);

CREATE INDEX idx_raw_imports_connection ON broker_raw_imports (connection_id, imported_at DESC);
CREATE INDEX idx_raw_imports_pending    ON broker_raw_imports (status)
    WHERE status = 'PENDING';
