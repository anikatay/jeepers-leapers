-- 1. User & Role Management
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(32) NOT NULL CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN', 'ROLE_ANALYST')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- Accounts
CREATE TABLE IF NOT EXISTS accounts (
    account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD', 'EUR', 'INR', 'GBP')),
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- CREATE TABLE IF NOT EXISTS never adds columns; databases created before updated_at need it
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- PII
CREATE TABLE IF NOT EXISTS user_pii (
    user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    first_name VARCHAR(64) NOT NULL,
    last_name VARCHAR(64) NOT NULL,
    date_of_birth DATE NOT NULL,
    ssn VARCHAR(11) UNIQUE NOT NULL,
    "address" TEXT,
    phone_number VARCHAR(15),
    email VARCHAR(255) UNIQUE NOT NULL
);

-- User Credentials
CREATE TABLE IF NOT EXISTS user_credentials (
    user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    password_hash VARCHAR(255) NOT NULL
);

-- 2. Tradable Instruments & Current Prices
CREATE TABLE IF NOT EXISTS instruments (
    instrument_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker VARCHAR(32) UNIQUE NOT NULL,
    "name" VARCHAR(255) NOT NULL,
    exchange_id VARCHAR(64) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    price_scale NUMERIC(12, 8) NOT NULL DEFAULT 1.00000000,
    current_price NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Exchanges
CREATE TABLE IF NOT EXISTS exchanges (
    exchange_id VARCHAR(64) PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    region VARCHAR(64) NOT NULL,
    timezone VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD', 'EUR', 'INR', 'GBP'))
);

-- 3. Customer Portfolio Holdings 
CREATE TABLE IF NOT EXISTS holdings (
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    quantity NUMERIC(18, 8) NOT NULL DEFAULT 0 CHECK (quantity > 0),
    CONSTRAINT uq_account_instrument UNIQUE (account_id, instrument_id)
);

-- 4. Overarching Trade Records
CREATE TABLE IF NOT EXISTS trades (
    trade_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(18, 8) NOT NULL,
    execution_price NUMERIC(18, 4) NOT NULL,
    trade_value NUMERIC(18, 4) GENERATED ALWAYS AS (quantity * execution_price) STORED,
    settlement_value NUMERIC(18, 4),
    settlement_currency VARCHAR(8),
    fx_rate NUMERIC(18, 8),
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Idempotent additions for databases created before multi-currency support.
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS accounts_currency_check;
ALTER TABLE accounts
    ADD CONSTRAINT accounts_currency_check
    CHECK (currency IN ('USD', 'EUR', 'INR', 'GBP'));

ALTER TABLE exchanges DROP CONSTRAINT IF EXISTS exchanges_currency_check;
ALTER TABLE exchanges
    ADD CONSTRAINT exchanges_currency_check
    CHECK (currency IN ('USD', 'EUR', 'INR', 'GBP'));

ALTER TABLE instruments
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8) NOT NULL DEFAULT 'USD';
ALTER TABLE instruments
    ADD COLUMN IF NOT EXISTS price_scale NUMERIC(12, 8) NOT NULL DEFAULT 1.00000000;

ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS settlement_value NUMERIC(18, 4);
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS settlement_currency VARCHAR(8);
ALTER TABLE trades
    ADD COLUMN IF NOT EXISTS fx_rate NUMERIC(18, 8);

UPDATE trades t
SET settlement_currency = a.currency,
    settlement_value = t.trade_value,
    fx_rate = 1
FROM accounts a
WHERE t.account_id = a.account_id
  AND t.settlement_currency IS NULL;

-- 5. Watchlist Hunter (docs/specs/watchlist-hunter.md, section 5.5)
CREATE TABLE IF NOT EXISTS hunter_specs (
    spec_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    "name" VARCHAR(80) NOT NULL,
    prompt VARCHAR(500) NOT NULL DEFAULT '',
    spec JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_hunter_specs_user ON hunter_specs (user_id);

-- The latest 10 runs per spec are kept; spec_hash lets an unchanged spec reuse its last run.
CREATE TABLE IF NOT EXISTS hunter_runs (
    run_id UUID PRIMARY KEY,
    spec_id UUID NOT NULL REFERENCES hunter_specs(spec_id) ON DELETE CASCADE,
    spec_hash CHAR(64) NOT NULL,
    ran_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    result JSONB NOT NULL,
    model VARCHAR(64) NOT NULL,
    match_count INT NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_hunter_runs_spec ON hunter_runs (spec_id, ran_at DESC);

CREATE TABLE IF NOT EXISTS watchlist_items (
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    ticker VARCHAR(32) NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source_spec_id UUID REFERENCES hunter_specs(spec_id) ON DELETE SET NULL,
    PRIMARY KEY (user_id, ticker)
);
