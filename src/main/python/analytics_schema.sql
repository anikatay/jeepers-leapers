-- Drop tables in reverse dependency order
DROP TABLE IF EXISTS risk_balance_exposure CASCADE;
DROP TABLE IF EXISTS risk_large_trades CASCADE;
DROP TABLE IF EXISTS risk_concentration CASCADE;
DROP TABLE IF EXISTS fact_daily_trade_summary CASCADE;
DROP TABLE IF EXISTS fact_trades CASCADE;
DROP TABLE IF EXISTS dim_instrument CASCADE;
DROP TABLE IF EXISTS dim_user CASCADE;
DROP TABLE IF EXISTS dim_date CASCADE;

-- Dimension Tables

-- dim_date
CREATE TABLE IF NOT EXISTS dim_date (
    date_key INTEGER PRIMARY KEY, -- format: YYYYMMDD, e.g., 20250825
    full_date DATE NOT NULL UNIQUE,
    year SMALLINT NOT NULL,
    quarter SMALLINT NOT NULL,
    month SMALLINT NOT NULL,
    month_name VARCHAR(16) NOT NULL,
    day_of_month SMALLINT NOT NULL,
    day_of_week SMALLINT NOT NULL, -- 0=Monday, 6=Sunday
    day_name VARCHAR(16) NOT NULL,
    is_weekend BOOLEAN NOT NULL,
    is_market_day BOOLEAN NOT NULL -- TRUE for Mon-Fri, simplified
);

-- dim_user
CREATE TABLE IF NOT EXISTS dim_user (
    user_key SERIAL PRIMARY KEY,
    user_id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    account_id UUID NOT NULL,
    currency VARCHAR(8) NOT NULL,
    UNIQUE (user_id, account_id)
);

-- dim_instrument
CREATE TABLE IF NOT EXISTS dim_instrument (
    instrument_key SERIAL PRIMARY KEY,
    instrument_id UUID NOT NULL UNIQUE,
    ticker VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    exchange_id VARCHAR(64) NOT NULL,
    exchange_name VARCHAR(255) NOT NULL,
    country VARCHAR(64) NOT NULL,
    exchange_timezone VARCHAR(32) NOT NULL,
    exchange_currency VARCHAR(8) NOT NULL
);

-- Fact Tables

-- fact_trades
CREATE TABLE IF NOT EXISTS fact_trades (
    trade_id UUID PRIMARY KEY,
    user_key INTEGER NOT NULL REFERENCES dim_user(user_key),
    instrument_key INTEGER NOT NULL REFERENCES dim_instrument(instrument_key),
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(18, 8) NOT NULL,
    execution_price NUMERIC(18, 4) NOT NULL,
    trade_value NUMERIC(18, 4) NOT NULL
);

-- fact_daily_trade_summary
CREATE TABLE IF NOT EXISTS fact_daily_trade_summary (
    date_key INTEGER NOT NULL REFERENCES dim_date(date_key),
    instrument_key INTEGER NOT NULL REFERENCES dim_instrument(instrument_key),
    trade_count INTEGER NOT NULL,
    total_volume NUMERIC(18, 8) NOT NULL,
    total_value NUMERIC(18, 4) NOT NULL,
    avg_price NUMERIC(18, 4) NOT NULL,
    min_price NUMERIC(18, 4) NOT NULL,
    max_price NUMERIC(18, 4) NOT NULL,
    PRIMARY KEY (date_key, instrument_key)
);

-- Risk Tables

-- risk_concentration
CREATE TABLE IF NOT EXISTS risk_concentration (
    user_key INTEGER NOT NULL REFERENCES dim_user(user_key),
    instrument_key INTEGER NOT NULL REFERENCES dim_instrument(instrument_key),
    holding_quantity NUMERIC(18, 8) NOT NULL,
    holding_value NUMERIC(18, 4) NOT NULL,
    portfolio_total_value NUMERIC(18, 4) NOT NULL,
    portfolio_pct NUMERIC(5, 2) NOT NULL,
    is_concentrated BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_key, instrument_key)
);

-- risk_large_trades
CREATE TABLE IF NOT EXISTS risk_large_trades (
    trade_id UUID PRIMARY KEY REFERENCES fact_trades(trade_id),
    user_key INTEGER NOT NULL REFERENCES dim_user(user_key),
    instrument_key INTEGER NOT NULL REFERENCES dim_instrument(instrument_key),
    trade_value NUMERIC(18, 4) NOT NULL,
    threshold NUMERIC(18, 4) NOT NULL,
    flagged_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- risk_balance_exposure
CREATE TABLE IF NOT EXISTS risk_balance_exposure (
    user_key INTEGER PRIMARY KEY REFERENCES dim_user(user_key),
    cash_balance NUMERIC(18, 4) NOT NULL,
    total_exposure NUMERIC(18, 4) NOT NULL,
    exposure_ratio NUMERIC(8, 4) NOT NULL
);
