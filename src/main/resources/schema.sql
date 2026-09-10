-- 1. User & Role Management
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN', 'ROLE_ANALYST')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Accounts
CREATE TABLE IF NOT EXISTS accounts (
    account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD', 'EUR', 'INR'),
    balance NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Tradable Instruments & Current Prices
CREATE TABLE IF NOT EXISTS instruments (
    instrument_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker VARCHAR(32) UNIQUE NOT NULL,
    "name" VARCHAR(255) NOT NULL,
    exchange_id VARCHAR(64) NOT NULL,
    current_price NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Exchanges
CREATE TABLE IF NOT EXISTS exchanges (
    exchange_id VARCHAR(64) PRIMARY KEY,
    "name" VARCHAR(255) NOT NULL,
    country VARCHAR(64) NOT NULL,
    timezone VARCHAR(32) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD' CHECK (currency IN ('USD', 'EUR', 'INR'))
);

-- 3. Customer Portfolio Holdings 
CREATE TABLE IF NOT EXISTS holdings (
    account_id UUID NOT NULL REFERENCES accounts(account_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    quantity NUMERIC(18, 8) NOT NULL DEFAULT 0,
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
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

