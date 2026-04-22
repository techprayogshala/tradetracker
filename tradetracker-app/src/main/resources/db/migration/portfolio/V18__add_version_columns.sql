-- =============================================================================
-- V18__add_version_columns.sql
-- Add missing version columns for optimistic locking
-- =============================================================================

ALTER TABLE accounts ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE portfolios ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE securities ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE trade_events ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE tax_parcels ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE parcel_disposals ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE dividends ADD COLUMN IF NOT EXISTS version BIGINT;

ALTER TABLE users ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS version BIGINT;

ALTER TABLE broker_connections ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE broker_raw_imports ADD COLUMN IF NOT EXISTS version BIGINT;

ALTER TABLE security_prices ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE fx_rates ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE corporate_actions ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE price_sync_log ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE price_watch_list ADD COLUMN IF NOT EXISTS version BIGINT;

ALTER TABLE notification_preferences ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE notification_outbox ADD COLUMN IF NOT EXISTS version BIGINT;

ALTER TABLE document_uploads ADD COLUMN IF NOT EXISTS version BIGINT;

ALTER TABLE cost_base_adjustments ADD COLUMN IF NOT EXISTS version BIGINT;
ALTER TABLE portfolio_snapshots ADD COLUMN IF NOT EXISTS version BIGINT;