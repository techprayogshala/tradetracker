-- =============================================================================
-- V2__portfolio_core.sql
-- Core portfolio schema: accounts, securities, trades, tax parcels
-- =============================================================================

-- ── Securities master ────────────────────────────────────────────────────────
CREATE TABLE securities (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    ticker          VARCHAR(20)  NOT NULL,
    exchange        VARCHAR(20)  NOT NULL,          -- ASX, NYSE, NASDAQ, LSE…
    name            VARCHAR(255),
    isin            VARCHAR(12)  UNIQUE,
    currency        CHAR(3)      NOT NULL,
    asset_class     VARCHAR(50)  NOT NULL DEFAULT 'EQUITY',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (ticker, exchange)
);

CREATE INDEX idx_securities_ticker   ON securities USING gin (ticker gin_trgm_ops);
CREATE INDEX idx_securities_exchange ON securities (exchange);

-- ── Portfolios ───────────────────────────────────────────────────────────────
CREATE TABLE portfolios (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id                 UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    base_currency           CHAR(3)      NOT NULL DEFAULT 'AUD',
    parcel_matching_strategy VARCHAR(50) NOT NULL DEFAULT 'FIFO',
    -- FIFO | LIFO | MINIMISE_CGT | SPECIFIC_PARCEL
    is_default              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_portfolios_user ON portfolios (user_id);

-- ── Accounts (brokerage accounts within a portfolio) ─────────────────────────
CREATE TABLE accounts (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    portfolio_id    UUID         NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    broker          VARCHAR(100),                   -- CommSec, SelfWealth, IBKR…
    account_number  VARCHAR(100),
    currency        CHAR(3)      NOT NULL DEFAULT 'AUD',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_accounts_portfolio ON accounts (portfolio_id);

-- ── Trade events ─────────────────────────────────────────────────────────────
CREATE TABLE trade_events (
    id              UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    account_id      UUID         NOT NULL REFERENCES accounts(id) ON DELETE RESTRICT,
    portfolio_id    UUID         NOT NULL REFERENCES portfolios(id),
    security_id     UUID         NOT NULL REFERENCES securities(id),
    trade_type      VARCHAR(30)  NOT NULL,
    -- BUY | SELL | DIVIDEND | RETURN_OF_CAPITAL | TRANSFER_IN | TRANSFER_OUT
    quantity        NUMERIC(20,8) NOT NULL,
    price           NUMERIC(20,8) NOT NULL,
    fees            NUMERIC(20,8) NOT NULL DEFAULT 0,
    currency        CHAR(3)      NOT NULL,
    fx_rate_to_base NUMERIC(20,8),                  -- NULL = same as base currency
    trade_date      DATE         NOT NULL,
    settlement_date DATE,
    notes           TEXT,
    source          VARCHAR(50)  NOT NULL DEFAULT 'MANUAL',
    -- MANUAL | BROKER_API | PDF_IMPORT | CSV_IMPORT
    external_ref    VARCHAR(255),                   -- Broker confirmation number
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_trade_events_portfolio ON trade_events (portfolio_id, trade_date DESC);
CREATE INDEX idx_trade_events_security  ON trade_events (security_id, trade_date DESC);
CREATE INDEX idx_trade_events_account   ON trade_events (account_id);
CREATE INDEX idx_trade_events_date      ON trade_events (trade_date DESC);

-- ── Tax parcels ──────────────────────────────────────────────────────────────
-- Every BUY creates one or more parcels. Parcels are the atomic unit of CGT.
CREATE TABLE tax_parcels (
    id                  UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    portfolio_id        UUID          NOT NULL REFERENCES portfolios(id),
    security_id         UUID          NOT NULL REFERENCES securities(id),
    source_trade_id     UUID          NOT NULL REFERENCES trade_events(id),
    quantity            NUMERIC(20,8) NOT NULL,
    quantity_remaining  NUMERIC(20,8) NOT NULL,     -- Decremented on disposal
    cost_per_unit       NUMERIC(20,8) NOT NULL,     -- Includes fees, split-adjusted
    currency            CHAR(3)       NOT NULL,
    fx_rate_to_base     NUMERIC(20,8),
    acquisition_date    DATE          NOT NULL,
    disposal_date       DATE,                       -- NULL = still open
    is_fully_disposed   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_parcels_portfolio    ON tax_parcels (portfolio_id, acquisition_date);
CREATE INDEX idx_parcels_security     ON tax_parcels (security_id);
CREATE INDEX idx_parcels_open         ON tax_parcels (portfolio_id, security_id)
    WHERE is_fully_disposed = FALSE;

-- ── Parcel disposal ledger ───────────────────────────────────────────────────
-- Links each SELL event to the specific parcels it consumed (after matching)
CREATE TABLE parcel_disposals (
    id                  UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    sell_trade_id       UUID          NOT NULL REFERENCES trade_events(id),
    parcel_id           UUID          NOT NULL REFERENCES tax_parcels(id),
    quantity_disposed   NUMERIC(20,8) NOT NULL,
    disposal_price      NUMERIC(20,8) NOT NULL,
    disposal_date       DATE          NOT NULL,
    capital_gain        NUMERIC(20,8),              -- Computed and stored for reporting
    cgt_discount_applied BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_disposals_sell_trade ON parcel_disposals (sell_trade_id);
CREATE INDEX idx_disposals_parcel     ON parcel_disposals (parcel_id);

-- ── Dividends ────────────────────────────────────────────────────────────────
CREATE TABLE dividends (
    id                  UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    trade_event_id      UUID          NOT NULL REFERENCES trade_events(id),
    portfolio_id        UUID          NOT NULL REFERENCES portfolios(id),
    security_id         UUID          NOT NULL REFERENCES securities(id),
    amount              NUMERIC(20,8) NOT NULL,
    franking_credits    NUMERIC(20,8) NOT NULL DEFAULT 0,
    franking_percentage NUMERIC(5,2)  NOT NULL DEFAULT 0,
    tax_withheld        NUMERIC(20,8) NOT NULL DEFAULT 0,
    ex_dividend_date    DATE,
    payment_date        DATE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_dividends_portfolio ON dividends (portfolio_id, payment_date DESC);
CREATE INDEX idx_dividends_security  ON dividends (security_id);
