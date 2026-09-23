-- ============================================================================
-- STAGING SCHEMA DDL
-- ============================================================================
-- Temporary working area for ETL pipeline
-- Raw staging tables for extract step
-- Dead-letter queue for data quality issues

-- Create staging schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS staging;

-- ============================================================================
-- RAW STAGING TABLES (for extract step)
-- ============================================================================

-- staging.trades_raw: Raw trade data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.trades_raw (
    trade_id UUID,
    account_id UUID,
    instrument_id UUID,
    side VARCHAR(10),
    quantity NUMERIC(18, 8),
    execution_price NUMERIC(18, 4),
    trade_value NUMERIC(18, 4),
    executed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    etl_run_id VARCHAR(100)  -- Airflow DAG run ID
);

-- staging.holdings_raw: Raw holdings data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.holdings_raw (
    account_id UUID,
    instrument_id UUID,
    quantity NUMERIC(18, 8),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    etl_run_id VARCHAR(100)
);

-- staging.accounts_raw: Raw account data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.accounts_raw (
    account_id UUID,
    user_id UUID,
    status VARCHAR(20),
    currency VARCHAR(3),
    balance NUMERIC(18, 4),
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    etl_run_id VARCHAR(100)
);

-- staging.instruments_raw: Raw instrument data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.instruments_raw (
    instrument_id UUID,
    ticker VARCHAR(20),
    instrument_name VARCHAR(200),
    exchange_id VARCHAR(10),
    current_price NUMERIC(18, 4),
    price_updated_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    etl_run_id VARCHAR(100)
);

-- staging.exchanges_raw: Raw exchange data extracted from OLTP
CREATE TABLE IF NOT EXISTS staging.exchanges_raw (
    exchange_id VARCHAR(10),
    exchange_name VARCHAR(100),
    region VARCHAR(50),
    timezone VARCHAR(50),
    currency VARCHAR(3),
    created_at TIMESTAMPTZ,
    etl_run_id VARCHAR(100)
);

-- ============================================================================
-- DEAD-LETTER QUEUE (for rejected rows)
-- ============================================================================

-- staging.dead_letter_queue: Rejected rows with error details (for manual investigation)
CREATE TABLE IF NOT EXISTS staging.dead_letter_queue (
    dlq_id BIGSERIAL PRIMARY KEY,
    etl_run_id VARCHAR(100) NOT NULL,
    operation VARCHAR(50) NOT NULL,  -- extract, transform, load
    source_table VARCHAR(100) NOT NULL,
    source_row_id VARCHAR(100),  -- Original row ID from source
    rejection_reason VARCHAR(500) NOT NULL,  -- Why was this row rejected?
    rejection_details TEXT,  -- Full error message/stacktrace
    raw_data JSONB NOT NULL,  -- Store the rejected row as JSON
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMPTZ,  -- When was this investigated?
    reviewed_by VARCHAR(100),  -- Who reviewed it?
    resolution VARCHAR(50),  -- FIXED, IGNORED, ESCALATED
    resolution_notes TEXT
);

-- Indexes for DLQ queries
CREATE INDEX IF NOT EXISTS idx_dlq_etl_run_id ON staging.dead_letter_queue(etl_run_id);
CREATE INDEX IF NOT EXISTS idx_dlq_created_at ON staging.dead_letter_queue(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dlq_source_table ON staging.dead_letter_queue(source_table);
CREATE INDEX IF NOT EXISTS idx_dlq_rejection_reason ON staging.dead_letter_queue(rejection_reason);
CREATE INDEX IF NOT EXISTS idx_dlq_resolved ON staging.dead_letter_queue(resolution);

-- ============================================================================
-- CLEANUP PROCEDURES
-- ============================================================================

-- Function: Delete staging tables after successful load
CREATE OR REPLACE FUNCTION staging.cleanup_staging_tables(p_etl_run_id VARCHAR)
RETURNS TABLE(table_name VARCHAR, rows_deleted INTEGER) AS $$
DECLARE
    v_trades_deleted INTEGER;
    v_holdings_deleted INTEGER;
    v_accounts_deleted INTEGER;
    v_instruments_deleted INTEGER;
    v_exchanges_deleted INTEGER;
BEGIN
    DELETE FROM staging.trades_raw WHERE etl_run_id = p_etl_run_id;
    GET DIAGNOSTICS v_trades_deleted = ROW_COUNT;
    
    DELETE FROM staging.holdings_raw WHERE etl_run_id = p_etl_run_id;
    GET DIAGNOSTICS v_holdings_deleted = ROW_COUNT;
    
    DELETE FROM staging.accounts_raw WHERE etl_run_id = p_etl_run_id;
    GET DIAGNOSTICS v_accounts_deleted = ROW_COUNT;
    
    DELETE FROM staging.instruments_raw WHERE etl_run_id = p_etl_run_id;
    GET DIAGNOSTICS v_instruments_deleted = ROW_COUNT;
    
    DELETE FROM staging.exchanges_raw WHERE etl_run_id = p_etl_run_id;
    GET DIAGNOSTICS v_exchanges_deleted = ROW_COUNT;
    
    RETURN QUERY SELECT 'trades_raw'::VARCHAR, v_trades_deleted
    UNION ALL SELECT 'holdings_raw'::VARCHAR, v_holdings_deleted
    UNION ALL SELECT 'accounts_raw'::VARCHAR, v_accounts_deleted
    UNION ALL SELECT 'instruments_raw'::VARCHAR, v_instruments_deleted
    UNION ALL SELECT 'exchanges_raw'::VARCHAR, v_exchanges_deleted;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- RECONCILIATION AND MONITORING VIEWS
-- ============================================================================

-- View: DLQ Summary
CREATE OR REPLACE VIEW staging.vw_dlq_summary AS
SELECT
    etl_run_id,
    operation,
    source_table,
    rejection_reason,
    COUNT(*) AS num_rejected_rows,
    MAX(created_at) AS last_rejected_at
FROM staging.dead_letter_queue
WHERE reviewed_at IS NULL  -- Only show unreviewed issues
GROUP BY etl_run_id, operation, source_table, rejection_reason
ORDER BY last_rejected_at DESC;

-- View: Staging table row counts
CREATE OR REPLACE VIEW staging.vw_staging_row_counts AS
SELECT
    'trades_raw' AS table_name,
    COUNT(*) AS row_count,
    COUNT(DISTINCT etl_run_id) AS num_etl_runs
FROM staging.trades_raw
UNION ALL
SELECT 'holdings_raw', COUNT(*), COUNT(DISTINCT etl_run_id) FROM staging.holdings_raw
UNION ALL
SELECT 'accounts_raw', COUNT(*), COUNT(DISTINCT etl_run_id) FROM staging.accounts_raw
UNION ALL
SELECT 'instruments_raw', COUNT(*), COUNT(DISTINCT etl_run_id) FROM staging.instruments_raw
UNION ALL
SELECT 'exchanges_raw', COUNT(*), COUNT(DISTINCT etl_run_id) FROM staging.exchanges_raw;

COMMIT;
