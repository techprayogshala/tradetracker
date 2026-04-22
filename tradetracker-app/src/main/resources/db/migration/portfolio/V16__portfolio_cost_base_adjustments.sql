-- =============================================================================
-- V3__portfolio_cost_base_adjustments.sql
-- Audit ledger for cost base reductions from Return of Capital distributions.
-- Each row records a per-parcel adjustment so the history is reconstructable.
-- =============================================================================

CREATE TABLE cost_base_adjustments (
    id                  UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    portfolio_id        UUID          NOT NULL REFERENCES portfolios(id),
    security_id         UUID          NOT NULL REFERENCES securities(id),
    trade_event_id      UUID          NOT NULL REFERENCES trade_events(id),
    parcel_id           UUID          NOT NULL REFERENCES tax_parcels(id),
    adjustment_type     VARCHAR(30)   NOT NULL DEFAULT 'RETURN_OF_CAPITAL',
    -- RETURN_OF_CAPITAL | SPLIT_ADJUSTMENT | CORPORATE_ACTION
    old_cost_per_unit   NUMERIC(20,8) NOT NULL,
    new_cost_per_unit   NUMERIC(20,8) NOT NULL,
    reduction_per_unit  NUMERIC(20,8) NOT NULL,
    quantity_held       NUMERIC(20,8) NOT NULL,
    effective_date      DATE          NOT NULL,
    notes               TEXT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_cost_adj_portfolio ON cost_base_adjustments (portfolio_id, effective_date DESC);
CREATE INDEX idx_cost_adj_parcel    ON cost_base_adjustments (parcel_id);
