-- =============================================================================
-- V1__document_uploads.sql
-- Tracks uploaded trade confirmations and parsing results.
-- =============================================================================

CREATE TABLE document_uploads (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    portfolio_id    UUID         NOT NULL REFERENCES portfolios(id),
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT,
    s3_key          TEXT         NOT NULL UNIQUE,   -- MinIO object path
    broker_detected VARCHAR(50),
    parse_status    VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | PARSING | PARSED | FAILED | REVIEWED
    parse_confidence NUMERIC(4,3),    -- 0.000–1.000
    parsed_trades   JSONB,            -- Extracted trade data before confirmation
    error_message   TEXT,
    reviewed_at     TIMESTAMPTZ,
    reviewed_by     UUID         REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_uploads_portfolio ON document_uploads (portfolio_id, created_at DESC);
CREATE INDEX idx_uploads_pending   ON document_uploads (parse_status)
    WHERE parse_status IN ('PENDING', 'PARSING');
