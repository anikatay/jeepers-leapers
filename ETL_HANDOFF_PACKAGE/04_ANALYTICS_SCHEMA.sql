-- ============================================================================
-- ANALYTICS SCHEMA (OLAP) DDL
-- ============================================================================
-- Star schema for business intelligence and analytics
-- Facts: Individual transactions and daily aggregates
-- Dimensions: Slowly-changing reference data (Type 1 - current only)

-- Create analytics schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS analytics;

-- ============================================================================
-- DIMENSION TABLES (Type 1 SCD: Current State Only)
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
    num_positions INTEGER DEFAULT 0,
    largest_position_value NUMERIC(18, 4) DEFAULT 0.00,
    
    -- Metadata
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    
    PRIMARY KEY (account_id, summary_date)
);

-- Indexes for common queries
CREATE INDEX IF NOT EXISTS idx_fact_daily_account_summary_account ON analytics.fact_daily_account_summary(account_id, summary_date);
CREATE INDEX IF NOT EXISTS idx_fact_daily_account_summary_date ON analytics.fact_daily_account_summary(summary_date);

-- ============================================================================
-- ANALYTICS VIEWS (For Easy Querying)
-- ============================================================================

-- View: Account P&L Summary (Today)
CREATE OR REPLACE VIEW analytics.vw_account_pnl_today AS
SELECT
    a.account_id,
    a.user_id,
    a.currency,
    s.summary_date,
    s.beginning_balance,
    s.ending_balance,
    s.daily_pnl,
    s.cumulative_pnl,
    s.num_buy_trades,
    s.num_sell_trades,
    s.total_shares_traded,
    s.total_volume_traded
FROM analytics.fact_daily_account_summary s
JOIN analytics.dim_accounts a ON s.account_id = a.account_id
WHERE s.summary_date = CURRENT_DATE
ORDER BY s.daily_pnl DESC;

-- View: Holdings by Account (Today)
CREATE OR REPLACE VIEW analytics.vw_holdings_today AS
SELECT
    h.account_id,
    d.ticker,
    d.instrument_name,
    e.exchange_name,
    h.quantity,
    h.cost_basis,
    d.current_price,
    h.market_value,
    h.unrealized_pnl,
    h.snapshot_date
FROM analytics.fact_daily_holdings h
JOIN analytics.dim_instruments d ON h.instrument_id = d.instrument_id
JOIN analytics.dim_exchanges e ON d.exchange_id = e.exchange_id
WHERE h.snapshot_date = CURRENT_DATE
ORDER BY h.account_id, h.market_value DESC;

-- View: Top Trades (Today)
CREATE OR REPLACE VIEW analytics.vw_trades_today AS
SELECT
    t.trade_id,
    a.account_id,
    d.ticker,
    t.side,
    t.quantity,
    t.execution_price,
    t.trade_value,
    t.realized_pnl,
    t.unrealized_pnl,
    t.executed_at
FROM analytics.fact_trades t
JOIN analytics.dim_accounts a ON t.account_id = a.account_id
JOIN analytics.dim_instruments d ON t.instrument_id = d.instrument_id
WHERE t.trade_date = CURRENT_DATE
ORDER BY t.executed_at DESC;
