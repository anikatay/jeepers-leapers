-- 1. User & Role Management
CREATE TABLE IF NOT EXISTS users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    role VARCHAR(32) NOT NULL CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_ADMIN', 'ROLE_ANALYST')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 2. Tradable Instruments & Current Prices
CREATE TABLE IF NOT EXISTS instruments (
    instrument_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    current_price NUMERIC(18, 4) NOT NULL DEFAULT 0.00,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- 3. Customer Portfolio Holdings
CREATE TABLE IF NOT EXISTS positions (
    position_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    quantity NUMERIC(18, 8) NOT NULL DEFAULT 0,
    CONSTRAINT uq_user_instrument UNIQUE (user_id, instrument_id)
);

-- 4. Overarching Trade Records
CREATE TABLE IF NOT EXISTS trades (
    trade_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(user_id),
    instrument_id UUID NOT NULL REFERENCES instruments(instrument_id),
    side VARCHAR(8) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    quantity NUMERIC(18, 8) NOT NULL,
    execution_price NUMERIC(18, 4) NOT NULL,
    trade_value NUMERIC(18, 4) GENERATED ALWAYS AS (quantity * execution_price) STORED,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

