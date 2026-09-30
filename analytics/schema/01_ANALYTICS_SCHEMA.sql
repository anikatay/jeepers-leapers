-- ============================================================================
-- ANALYTICS STAR SCHEMA - OLAP Database
-- Phase 2: Core dimensions and fact tables (without data)
-- ============================================================================
-- This schema is designed for analytical queries on the paysprint_analytics database.
-- Data flows from OLTP staging tables through Python transformations into these tables.
--
-- Star Schema Pattern:
--   Dimensions (reference data): dim_exchanges, dim_instruments, dim_accounts, dim_dates
--   Facts (events/snapshots): fact_trades, fact_daily_holdings, fact_daily_account_summary
--   Audit: audit_trail (compliance log)
-- ============================================================================

-- Ensure analytics schema exists
CREATE SCHEMA IF NOT EXISTS analytics;

-- ============================================================================
-- DIMENSION TABLES (Reference Data)
-- ============================================================================

-- dim_exchanges: Trading venues
-- Loaded from: staging.exchanges_raw (Phase 1)
-- Rows expected: 2 (NYSE, NASDAQ)
CREATE TABLE IF NOT EXISTS analytics.dim_exchanges (
    exchange_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    region VARCHAR(64) NOT NULL,
    timezone VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL CHECK (currency IN ('USD', 'EUR', 'INR')),
    etl_run_id VARCHAR(255),
    etl_timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_exchanges_region 
    ON analytics.dim_exchanges(region);

COMMENT ON TABLE analytics.dim_exchanges IS 'Trading exchanges (NYSE, NASDAQ, etc). Loaded from staging.exchanges_raw.';
COMMENT ON COLUMN analytics.dim_exchanges.exchange_id IS 'Primary key: exchange identifier';
COMMENT ON COLUMN analytics.dim_exchanges.etl_run_id IS 'Audit: which ETL run created this row';


-- dim_instruments: Tradable securities (stocks, ETFs, etc.)
-- Loaded from: staging.instruments_raw (Phase 1)
-- Rows expected: 7
CREATE TABLE IF NOT EXISTS analytics.dim_instruments (
    instrument_id UUID PRIMARY KEY,
    ticker VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    exchange_id VARCHAR(64) NOT NULL REFERENCES analytics.dim_exchanges(exchange_id),
    current_price NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ,
    etl_run_id VARCHAR(255),
    etl_timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_instruments_ticker 
    ON analytics.dim_instruments(ticker);
CREATE INDEX IF NOT EXISTS idx_instruments_exchange_id 
    ON analytics.dim_instruments(exchange_id);

COMMENT ON TABLE analytics.dim_instruments IS 'Tradable securities/instruments. Loaded from staging.instruments_raw.';
COMMENT ON COLUMN analytics.dim_instruments.ticker IS 'Stock symbol (AAPL, GOOGL, etc)';
COMMENT ON COLUMN analytics.dim_instruments.exchange_id IS 'Foreign key: which exchange lists this instrument';


-- dim_accounts: Trading accounts (customer portfolios)
-- Loaded from: staging.accounts_raw (Phase 1)
-- Rows expected: 5
CREATE TABLE IF NOT EXISTS analytics.dim_accounts (
    account_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    currency VARCHAR(8) NOT NULL CHECK (currency IN ('USD', 'EUR', 'INR')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    etl_run_id VARCHAR(255),
    etl_timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id 
    ON analytics.dim_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_accounts_status 
    ON analytics.dim_accounts(status);

COMMENT ON TABLE analytics.dim_accounts IS 'Trading accounts owned by users. Loaded from staging.accounts_raw.';
COMMENT ON COLUMN analytics.dim_accounts.user_id IS 'User who owns this account';
COMMENT ON COLUMN analytics.dim_accounts.status IS 'ACTIVE or INACTIVE account';


-- dim_dates: Calendar dimension (2024-2028)
-- Loaded from: Python script generate_dates.py (Phase 2)
-- Rows expected: 1460+ (all dates 2024-2028)
CREATE TABLE IF NOT EXISTS analytics.dim_dates (
    date DATE PRIMARY KEY,
    year INTEGER NOT NULL,
    month INTEGER NOT NULL CHECK (month BETWEEN 1 AND 12),
    quarter INTEGER NOT NULL CHECK (quarter BETWEEN 1 AND 4),
    week_of_year INTEGER CHECK (week_of_year BETWEEN 1 AND 53),
    day_of_week VARCHAR(9) NOT NULL,
    day_of_month INTEGER NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    is_trading_day BOOLEAN DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_dates_year_month 
    ON analytics.dim_dates(year, month);
CREATE INDEX IF NOT EXISTS idx_dates_trading_day 
    ON analytics.dim_dates(is_trading_day);

COMMENT ON TABLE analytics.dim_dates IS 'Calendar dimension. Generated for 2024-2028. is_trading_day excludes weekends.';
COMMENT ON COLUMN analytics.dim_dates.is_trading_day IS 'TRUE if market open (Mon-Fri). FALSE for weekends.';

-- ============================================================================
-- FACT TABLES (Transactional/Event Data)
-- ============================================================================

-- fact_trades: Individual trade transactions
-- Loaded by: Phase 3 (transform_pnl.py) with P&L calculations
-- Source: staging.trades_raw → FIFO matching → P&L calculation
-- Columns added in Phase 3: entry_price, exit_price, realized_pnl, unrealized_pnl, pnl_status
CREATE TABLE IF NOT EXISTS analytics.fact_trades (
    trade_id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES analytics.dim_accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES analytics.dim_instruments(instrument_id),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(18, 8) NOT NULL,
    execution_price NUMERIC(18, 4) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL,
    executed_date DATE NOT NULL REFERENCES analytics.dim_dates(date),
    
    -- P&L columns (populated in Phase 3)
    entry_price NUMERIC(18, 4),
    exit_price NUMERIC(18, 4),
    realized_pnl NUMERIC(18, 4),
    unrealized_pnl NUMERIC(18, 4),
    pnl_status VARCHAR(32) CHECK (pnl_status IN ('MATCHED', 'UNMATCHED', 'PARTIAL')),
    
    -- Audit columns
    etl_run_id VARCHAR(255),
    etl_timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_fact_trades_account_date 
    ON analytics.fact_trades(account_id, executed_date);
CREATE INDEX IF NOT EXISTS idx_fact_trades_instrument_id 
    ON analytics.fact_trades(instrument_id);
CREATE INDEX IF NOT EXISTS idx_fact_trades_side 
    ON analytics.fact_trades(side);
CREATE INDEX IF NOT EXISTS idx_fact_trades_executed_date 
    ON analytics.fact_trades(executed_date);

COMMENT ON TABLE analytics.fact_trades IS 'Individual trade transactions. Populated by Phase 3 with P&L calculations (FIFO matching).';
COMMENT ON COLUMN analytics.fact_trades.entry_price IS 'Entry price (from matched BUY trade in FIFO queue)';
COMMENT ON COLUMN analytics.fact_trades.exit_price IS 'Exit price (from matched SELL trade or current price if unmatched)';
COMMENT ON COLUMN analytics.fact_trades.realized_pnl IS '(entry_price - exit_price) * quantity for closed positions';
COMMENT ON COLUMN analytics.fact_trades.unrealized_pnl IS '(entry_price - current_price) * quantity for open positions';
COMMENT ON COLUMN analytics.fact_trades.pnl_status IS 'MATCHED (fully paired), UNMATCHED (orphan), PARTIAL (partially paired)';


-- fact_daily_holdings: Daily portfolio position snapshots
-- Loaded by: Phase 4 (transform_holdings.py)
-- Source: staging.holdings_raw → daily snapshots by account/instrument/date
-- One row per account+instrument+date combination
CREATE TABLE IF NOT EXISTS analytics.fact_daily_holdings (
    account_id UUID NOT NULL REFERENCES analytics.dim_accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES analytics.dim_instruments(instrument_id),
    date DATE NOT NULL REFERENCES analytics.dim_dates(date),
    quantity NUMERIC(18, 8) NOT NULL DEFAULT 0,
    market_value NUMERIC(18, 4),
    
    -- Audit columns
    etl_run_id VARCHAR(255),
    etl_timestamp TIMESTAMPTZ DEFAULT NOW(),
    
    PRIMARY KEY (account_id, instrument_id, date)
);

CREATE INDEX IF NOT EXISTS idx_holdings_account_date 
    ON analytics.fact_daily_holdings(account_id, date);
CREATE INDEX IF NOT EXISTS idx_holdings_instrument_date 
    ON analytics.fact_daily_holdings(instrument_id, date);

COMMENT ON TABLE analytics.fact_daily_holdings IS 'Daily portfolio snapshots. One row per account/instrument/date. Populated by Phase 4.';
COMMENT ON COLUMN analytics.fact_daily_holdings.quantity IS 'Units held at end of this date';
COMMENT ON COLUMN analytics.fact_daily_holdings.market_value IS 'quantity * current_price (calculated in Phase 4)';


-- fact_daily_account_summary: Daily account-level aggregates
-- Loaded by: Phase 5 (transform_summary.py)
-- Source: fact_trades + fact_daily_holdings grouped by account/date
-- One row per account+date combination
CREATE TABLE IF NOT EXISTS analytics.fact_daily_account_summary (
    account_id UUID NOT NULL REFERENCES analytics.dim_accounts(account_id),
    date DATE NOT NULL REFERENCES analytics.dim_dates(date),
    opening_balance NUMERIC(18, 4),
    closing_balance NUMERIC(18, 4),
    daily_pnl NUMERIC(18, 4) DEFAULT 0,
    daily_trades INTEGER DEFAULT 0,
    buy_trades INTEGER DEFAULT 0,
    sell_trades INTEGER DEFAULT 0,
    
    -- Audit columns
    etl_run_id VARCHAR(255),
    etl_timestamp TIMESTAMPTZ DEFAULT NOW(),
    
    PRIMARY KEY (account_id, date)
);

CREATE INDEX IF NOT EXISTS idx_summary_account_date 
    ON analytics.fact_daily_account_summary(account_id, date);
CREATE INDEX IF NOT EXISTS idx_summary_date 
    ON analytics.fact_daily_account_summary(date);

COMMENT ON TABLE analytics.fact_daily_account_summary IS 'Daily account aggregates. One row per account/date. Populated by Phase 5.';
COMMENT ON COLUMN analytics.fact_daily_account_summary.daily_pnl IS 'Sum of realized_pnl for trades on this date';
COMMENT ON COLUMN analytics.fact_daily_account_summary.daily_trades IS 'Count of trades on this date';

-- ============================================================================
-- AUDIT TABLE (Compliance & Logging)
-- ============================================================================

-- audit_trail: Immutable log of all ETL operations
-- Logged by: All transformation phases (3-6)
-- Purpose: Compliance, debugging, change tracking
CREATE TABLE IF NOT EXISTS analytics.audit_trail (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    table_name VARCHAR(255) NOT NULL,
    operation VARCHAR(32) NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE', 'TRUNCATE')),
    record_id VARCHAR(255),
    user_id VARCHAR(255),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    row_count INTEGER,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS' CHECK (status IN ('SUCCESS', 'FAILED')),
    error_message TEXT,
    etl_run_id VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_audit_table_timestamp 
    ON analytics.audit_trail(table_name, timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_etl_run_id 
    ON analytics.audit_trail(etl_run_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp 
    ON analytics.audit_trail(timestamp);

COMMENT ON TABLE analytics.audit_trail IS 'Immutable log of all ETL operations. Compliance record. Indexed for debugging.';
COMMENT ON COLUMN analytics.audit_trail.operation IS 'Type of operation: INSERT, UPDATE, DELETE, TRUNCATE';
COMMENT ON COLUMN analytics.audit_trail.status IS 'SUCCESS or FAILED. If FAILED, check error_message.';
COMMENT ON COLUMN analytics.audit_trail.etl_run_id IS 'Links to DataExtractor run_id for traceability';

-- ============================================================================
-- LOAD DIMENSION DATA FROM STAGING
-- ============================================================================
-- These queries load reference data that was extracted in Phase 1.
-- Using ON CONFLICT to make them idempotent (safe to re-run).

-- Load exchanges from staging
INSERT INTO analytics.dim_exchanges 
    (exchange_id, name, region, timezone, currency, etl_run_id, etl_timestamp)
SELECT DISTINCT 
    exchange_id, name, region, timezone, currency, etl_run_id, etl_timestamp
FROM staging.exchanges_raw
ON CONFLICT (exchange_id) DO UPDATE SET
    name = EXCLUDED.name,
    region = EXCLUDED.region,
    timezone = EXCLUDED.timezone,
    currency = EXCLUDED.currency,
    etl_run_id = EXCLUDED.etl_run_id,
    etl_timestamp = EXCLUDED.etl_timestamp;

-- Load instruments from staging
INSERT INTO analytics.dim_instruments 
    (instrument_id, ticker, name, exchange_id, current_price, updated_at, etl_run_id, etl_timestamp)
SELECT 
    instrument_id, ticker, name, exchange_id, current_price, updated_at, etl_run_id, etl_timestamp
FROM staging.instruments_raw
ON CONFLICT (instrument_id) DO UPDATE SET
    ticker = EXCLUDED.ticker,
    name = EXCLUDED.name,
    exchange_id = EXCLUDED.exchange_id,
    current_price = EXCLUDED.current_price,
    updated_at = EXCLUDED.updated_at,
    etl_run_id = EXCLUDED.etl_run_id,
    etl_timestamp = EXCLUDED.etl_timestamp;

-- Load accounts from staging
INSERT INTO analytics.dim_accounts 
    (account_id, user_id, currency, status, created_at, etl_run_id, etl_timestamp)
SELECT 
    account_id, user_id, currency, status, created_at, etl_run_id, etl_timestamp
FROM staging.accounts_raw
ON CONFLICT (account_id) DO UPDATE SET
    user_id = EXCLUDED.user_id,
    currency = EXCLUDED.currency,
    status = EXCLUDED.status,
    created_at = EXCLUDED.created_at,
    etl_run_id = EXCLUDED.etl_run_id,
    etl_timestamp = EXCLUDED.etl_timestamp;

-- ============================================================================
-- VERIFICATION QUERIES (for manual inspection)
-- ============================================================================
-- Uncomment to verify schema creation after running this file:
/*
SELECT 
    'Exchanges' as table_name,
    COUNT(*) as row_count
FROM analytics.dim_exchanges

UNION ALL

SELECT 
    'Instruments',
    COUNT(*)
FROM analytics.dim_instruments

UNION ALL

SELECT 
    'Accounts',
    COUNT(*)
FROM analytics.dim_accounts

UNION ALL

SELECT 
    'Trades (fact)',
    COUNT(*)
FROM analytics.fact_trades

UNION ALL

SELECT 
    'Holdings (fact)',
    COUNT(*)
FROM analytics.fact_daily_holdings

UNION ALL

SELECT 
    'Summaries (fact)',
    COUNT(*)
FROM analytics.fact_daily_account_summary

UNION ALL

SELECT 
    'Audit Trail',
    COUNT(*)
FROM analytics.audit_trail

ORDER BY 1;
*/
