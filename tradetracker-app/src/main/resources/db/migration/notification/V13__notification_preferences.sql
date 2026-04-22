-- =============================================================================
-- V1__notification_preferences.sql
-- Per-user notification preferences and outbox for reliable delivery.
-- =============================================================================

CREATE TABLE notification_preferences (
    user_id             UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    channel             VARCHAR(20) NOT NULL,   -- EMAIL | WEBHOOK
    event_type          VARCHAR(50) NOT NULL,   -- TRADE_CONFIRMED | DAILY_SUMMARY | CGT_REMINDER
    enabled             BOOLEAN     NOT NULL DEFAULT TRUE,
    webhook_url         TEXT,
    PRIMARY KEY (user_id, channel, event_type)
);

-- Transactional outbox pattern — guarantees at-least-once delivery
-- even if the mail server is temporarily unreachable.
CREATE TABLE notification_outbox (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    event_type      VARCHAR(50)  NOT NULL,
    payload         JSONB        NOT NULL,
    channel         VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    -- PENDING | SENT | FAILED | SKIPPED
    attempts        SMALLINT     NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMPTZ,
    sent_at         TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending ON notification_outbox (status, created_at)
    WHERE status = 'PENDING';
CREATE INDEX idx_outbox_user    ON notification_outbox (user_id, created_at DESC);
