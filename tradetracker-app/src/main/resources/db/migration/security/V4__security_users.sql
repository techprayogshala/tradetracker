-- =============================================================================
-- V1__security_users.sql
-- User profiles linked to Keycloak subject IDs
-- =============================================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    keycloak_sub    VARCHAR(255) NOT NULL UNIQUE,   -- Keycloak subject claim
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    base_currency   CHAR(3)      NOT NULL DEFAULT 'AUD',
    tax_country     CHAR(2)      NOT NULL DEFAULT 'AU',  -- ISO 3166-1 alpha-2
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_keycloak_sub ON users (keycloak_sub);

-- Audit log — immutable append-only record of all data changes
CREATE TABLE audit_log (
    id              BIGSERIAL PRIMARY KEY,
    user_id         UUID         REFERENCES users(id),
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       UUID         NOT NULL,
    action          VARCHAR(50)  NOT NULL,  -- CREATE, UPDATE, DELETE, OVERRIDE
    old_value       JSONB,
    new_value       JSONB,
    reason          TEXT,                   -- Required for cost-base overrides
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_user   ON audit_log (user_id, created_at DESC);
