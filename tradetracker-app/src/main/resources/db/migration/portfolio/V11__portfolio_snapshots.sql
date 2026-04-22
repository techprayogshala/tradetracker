-- =============================================================================
-- V2__portfolio_snapshots.sql
-- Daily EOD portfolio value snapshots — powers the TWR time-series chart.
-- Written by PortfolioSnapshotJob each evening after price sync.
-- =============================================================================

CREATE TABLE portfolio_snapshots (
    portfolio_id    UUID          NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    snapshot_date   DATE          NOT NULL,
    market_value    NUMERIC(20,4) NOT NULL,
    cost_base       NUMERIC(20,4) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (portfolio_id, snapshot_date)
);

-- TimescaleDB hypertable for efficient time-range queries (chart data)
SELECT create_hypertable('portfolio_snapshots', 'snapshot_date',
    partitioning_column => 'portfolio_id',
    number_partitions    => 4,
    chunk_time_interval  => INTERVAL '1 year',
    if_not_exists        => TRUE
);

CREATE INDEX idx_snapshots_portfolio_date
    ON portfolio_snapshots (portfolio_id, snapshot_date DESC);
