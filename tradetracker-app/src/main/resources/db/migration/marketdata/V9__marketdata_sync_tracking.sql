-- =============================================================================
-- V2__marketdata_sync_tracking.sql
-- Adds a watch list so the scheduler knows which securities to sync prices for.
-- Also adds the Quartz scheduler tables (Spring Boot auto-creates these, but
-- we add a schema marker migration so Flyway tracks the state).
-- =============================================================================

-- Securities that should have prices actively synced
CREATE TABLE IF NOT EXISTS price_watch_list (
    security_id     UUID        NOT NULL REFERENCES securities(id) ON DELETE CASCADE,
    added_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_synced_at  TIMESTAMPTZ,
    sync_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (security_id)
);

-- When a portfolio holds a security, auto-add it to the watch list via trigger
CREATE OR REPLACE FUNCTION add_to_watch_list()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO price_watch_list (security_id)
    VALUES (NEW.security_id)
    ON CONFLICT (security_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_parcel_watch_list
    AFTER INSERT ON tax_parcels
    FOR EACH ROW EXECUTE FUNCTION add_to_watch_list();

-- Index to efficiently find stale prices (not synced in > 24h)
CREATE INDEX idx_watch_list_stale
    ON price_watch_list (last_synced_at)
    WHERE sync_enabled = TRUE;
