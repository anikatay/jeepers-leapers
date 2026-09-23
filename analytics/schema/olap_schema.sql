-- ============================================================================
-- ANALYTICS SCHEMA (OLAP) DDL
-- ============================================================================
-- Star schema for business intelligence and analytics
-- Facts: Individual transactions and daily aggregates
-- Dimensions: Slowly-changing reference data (Type 1 - current only)
-- Audit: Compliance and data quality logging

-- Create analytics schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS analytics;

-- ============================================================================
-- DIMENSION TABLES
-- ============================================================================

-- dim_exchanges: Trading venues (NYSE, NASDAQ, etc.)
CREATE TABLE IF NOT EXISTS analytics.dim_exchanges (
    exchange_id VARCHAR(10) PRIMARY KEY,
    exchange_name VARCHAR(100) NOT NULL,
    region VARCHAR(50),
    timezone VARCHAR(50),
    currency VARCHAR(3),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dim_exchanges_currency ON analytics.dim_exchanges(currency);

-- dim_accounts: Trading accounts (links to users)
CREATE TABLE IF NOT EXISTS analytics.dim_accounts (
    account_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    account_status VARCHAR(20) DEFAULT 'ACTIVE',  -- ACTIVE, INACTIVE, SUSPENDED
    currency VARCHAR(3) NOT NULL,  -- USD, EUR, INR
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dim_accounts_user_id ON analytics.dim_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_dim_accounts_status ON analytics.dim_accounts(account_status);

-- dim_instruments: Tradable securities (stocks, ETFs, etc.)
CREATE TABLE IF NOT EXISTS analytics.dim_instruments (
    instrument_id UUID PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL UNIQUE,
    instrument_name VARCHAR(200),
    exchange_id VARCHAR(10) NOT NULL REFERENCES analytics.dim_exchanges(exchange_id),
    current_price NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    price_updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    sector VARCHAR(100),  -- Technology, Finance, Healthcare, etc. (extensible)
    instrument_type VARCHAR(50),  -- STOCK, ETF, OPTION, etc. (extensible)
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dim_instruments_ticker ON analytics.dim_instruments(ticker);
CREATE INDEX IF NOT EXISTS idx_dim_instruments_exchange_id ON analytics.dim_instruments(exchange_id);
CREATE INDEX IF NOT EXISTS idx_dim_instruments_sector ON analytics.dim_instruments(sector);

-- dim_dates: Date dimension for time-series queries
CREATE TABLE IF NOT EXISTS analytics.dim_dates (
    date_key DATE PRIMARY KEY,
    date_full DATE NOT NULL UNIQUE,
    year INTEGER NOT NULL,
    quarter INTEGER NOT NULL,
    month INTEGER NOT NULL,
    month_name VARCHAR(20) NOT NULL,
    day_of_month INTEGER NOT NULL,
    day_of_week INTEGER NOT NULL,
    day_name VARCHAR(20) NOT NULL,
    week_of_year INTEGER NOT NULL,
    is_weekday BOOLEAN NOT NULL,
    is_trading_day BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_dim_dates_year_month ON analytics.dim_dates(year, month);
CREATE INDEX IF NOT EXISTS idx_dim_dates_trading_day ON analytics.dim_dates(is_trading_day);

-- ============================================================================
-- FACT TABLES
-- ============================================================================

-- fact_trades: Individual trade transactions with P&L
CREATE TABLE IF NOT EXISTS analytics.fact_trades (
    trade_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES analytics.dim_accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES analytics.dim_instruments(instrument_id),
    exchange_id VARCHAR(10) NOT NULL REFERENCES analytics.dim_exchanges(exchange_id),
    trade_date DATE NOT NULL,  -- Date key for joining with dim_dates
    
    -- Trade details
    side VARCHAR(10) NOT NULL,  -- BUY or SELL
    quantity NUMERIC(18, 8) NOT NULL,
    execution_price NUMERIC(18, 4) NOT NULL,
    trade_value NUMERIC(18, 4) NOT NULL,  -- quantity * execution_price
    executed_at TIMESTAMPTZ NOT NULL,
    
    -- P&L calculations (hybrid approach)
    realized_pnl NUMERIC(18, 4) DEFAULT 0.00,  -- P&L from matched buy/sell pairs (FIFO)
    unrealized_pnl NUMERIC(18, 4) DEFAULT 0.00,  -- P&L from open positions (daily snapshot)
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_fact_trades_account_date ON analytics.fact_trades(account_id, trade_date);
CREATE INDEX IF NOT EXISTS idx_fact_trades_instrument_date ON analytics.fact_trades(instrument_id, trade_date);
CREATE INDEX IF NOT EXISTS idx_fact_trades_exchange_date ON analytics.fact_trades(exchange_id, trade_date);
CREATE INDEX IF NOT EXISTS idx_fact_trades_executed_at ON analytics.fact_trades(executed_at);

-- fact_daily_holdings: Daily portfolio snapshots (account + instrument positions)
CREATE TABLE IF NOT EXISTS analytics.fact_daily_holdings (
    account_id UUID NOT NULL REFERENCES analytics.dim_accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES analytics.dim_instruments(instrument_id),
    snapshot_date DATE NOT NULL,  -- Date key for joining with dim_dates
    
    -- Holdings details
    quantity NUMERIC(18, 8) NOT NULL DEFAULT 0.00,
    cost_basis NUMERIC(18, 4) NOT NULL DEFAULT 0.00,  -- Average cost per share
    market_value NUMERIC(18, 4) NOT NULL DEFAULT 0.00,  -- quantity * current_price
    unrealized_pnl NUMERIC(18, 4) NOT NULL DEFAULT 0.00,  -- market_value - cost_basis
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (account_id, instrument_id, snapshot_date)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_fact_daily_holdings_account_date ON analytics.fact_daily_holdings(account_id, snapshot_date);
CREATE INDEX IF NOT EXISTS idx_fact_daily_holdings_instrument_date ON analytics.fact_daily_holdings(instrument_id, snapshot_date);

-- fact_daily_account_summary: Daily account-level aggregates
CREATE TABLE IF NOT EXISTS analytics.fact_daily_account_summary (
    account_id UUID NOT NULL REFERENCES analytics.dim_accounts(account_id),
    summary_date DATE NOT NULL,  -- Date key for joining with dim_dates
    
    -- Account summary metrics
    beginning_balance NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    ending_balance NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    daily_pnl NUMERIC(18, 4) NOT NULL DEFAULT 0.00,  -- realized_pnl + unrealized_pnl changes
    cumulative_pnl NUMERIC(18, 4) NOT NULL DEFAULT 0.00,  -- Running total P&L
    
    -- Trade activity
    num_buy_trades INTEGER DEFAULT 0,
    num_sell_trades INTEGER DEFAULT 0,
    total_shares_traded NUMERIC(18, 8) DEFAULT 0.00,
    total_volume_traded NUMERIC(18, 4) DEFAULT 0.00,
    
    -- Position metrics
    num_positions INTEGER DEFAULT 0,  -- Number of open positions
    largest_position_value NUMERIC(18, 4) DEFAULT 0.00,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (account_id, summary_date)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_fact_daily_account_summary_date ON analytics.fact_daily_account_summary(summary_date);

-- ============================================================================
-- AUDIT TABLE
-- ============================================================================

-- audit_trail: Immutable log of all ETL operations (compliance)
CREATE TABLE IF NOT EXISTS analytics.audit_trail (
    audit_id BIGSERIAL PRIMARY KEY,
    operation_timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    table_name VARCHAR(100) NOT NULL,  -- Table being loaded
    operation VARCHAR(50) NOT NULL,  -- INSERT, UPDATE, DELETE, RECONCILE, VALIDATE
    rows_affected INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL,  -- SUCCESS, FAILED, WARNING
    error_message TEXT,  -- Only populated if status = FAILED
    etl_run_id VARCHAR(100),  -- DAG run ID from Airflow
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for audit queries
CREATE INDEX IF NOT EXISTS idx_audit_trail_timestamp ON analytics.audit_trail(operation_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_audit_trail_table_status ON analytics.audit_trail(table_name, status);
CREATE INDEX IF NOT EXISTS idx_audit_trail_etl_run_id ON analytics.audit_trail(etl_run_id);

-- ============================================================================
-- MATERIALIZED VIEWS (for common BI queries)
-- ============================================================================

-- View: Account P&L trend (daily cumulative)
CREATE OR REPLACE VIEW analytics.vw_account_pnl_trend AS
SELECT
    account_id,
    summary_date,
    ending_balance,
    daily_pnl,
    cumulative_pnl,
    num_buy_trades + num_sell_trades AS total_trades,
    total_volume_traded
FROM analytics.fact_daily_account_summary
ORDER BY account_id, summary_date;

-- View: Holdings by account and instrument
CREATE OR REPLACE VIEW analytics.vw_current_holdings AS
SELECT
    h.account_id,
    h.instrument_id,
    i.ticker,
    i.instrument_name,
    i.exchange_id,
    e.exchange_name,
    h.quantity,
    h.cost_basis,
    i.current_price,
    h.market_value,
    h.unrealized_pnl,
    h.snapshot_date
FROM analytics.fact_daily_holdings h
INNER JOIN analytics.dim_instruments i ON h.instrument_id = i.instrument_id
INNER JOIN analytics.dim_exchanges e ON i.exchange_id = e.exchange_id
WHERE h.snapshot_date = CURRENT_DATE;

-- View: Trade execution summary by account
CREATE OR REPLACE VIEW analytics.vw_trade_summary AS
SELECT
    account_id,
    DATE(executed_at) AS trade_date,
    side,
    COUNT(*) AS num_trades,
    SUM(quantity) AS total_quantity,
    SUM(trade_value) AS total_value,
    ROUND(AVG(execution_price), 4) AS avg_price,
    MIN(execution_price) AS min_price,
    MAX(execution_price) AS max_price
FROM analytics.fact_trades
GROUP BY account_id, DATE(executed_at), side;

-- ============================================================================
-- GRANTS AND PERMISSIONS
-- ============================================================================

-- Grant read access to analytics schema for backend/frontend users
-- GRANT USAGE ON SCHEMA analytics TO analytics_reader;
-- GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO analytics_reader;
-- GRANT SELECT ON ALL MATERIALIZED VIEWS IN SCHEMA analytics TO analytics_reader;

COMMIT;
