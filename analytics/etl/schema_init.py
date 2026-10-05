"""
Phase 0: Database Schema Initialization

Creates all necessary schemas and tables for the analytics ETL pipeline.
This should be run ONCE during initial database setup, before any ETL phases.

Usage:
    python3 -c "from etl.schema_init import initialize_database; initialize_database()"
    
Or from command line:
    python3 analytics/etl/schema_init.py
"""

import logging
import sys
from sqlalchemy import text

from .config import get_staging_engine, get_analytics_engine

logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s'
)
logger = logging.getLogger(__name__)


# SQL to create staging schema and tables
STAGING_SCHEMA_SQL = """
-- Create staging schema if it doesn't exist
CREATE SCHEMA IF NOT EXISTS staging;

-- Create staging tables for raw data from OLTP
CREATE TABLE IF NOT EXISTS staging.exchanges_raw (
    exchange_id UUID,
    code VARCHAR(10),
    name VARCHAR(100),
    region VARCHAR(50),
    etl_run_id VARCHAR(50),
    etl_timestamp TIMESTAMP,
    source_table VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS staging.accounts_raw (
    account_id UUID,
    user_id UUID,
    username VARCHAR(50),
    email VARCHAR(100),
    created_at TIMESTAMP,
    etl_run_id VARCHAR(50),
    etl_timestamp TIMESTAMP,
    source_table VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS staging.instruments_raw (
    instrument_id UUID,
    ticker VARCHAR(10),
    name VARCHAR(100),
    exchange_id UUID,
    current_price DECIMAL(15, 4),
    etl_run_id VARCHAR(50),
    etl_timestamp TIMESTAMP,
    source_table VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS staging.holdings_raw (
    holding_id UUID,
    account_id UUID,
    instrument_id UUID,
    quantity DECIMAL(15, 2),
    as_of_date DATE,
    etl_run_id VARCHAR(50),
    etl_timestamp TIMESTAMP,
    source_table VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS staging.trades_raw (
    trade_id UUID,
    account_id UUID,
    instrument_id UUID,
    side VARCHAR(10),
    quantity DECIMAL(15, 2),
    execution_price DECIMAL(15, 4),
    executed_at TIMESTAMP,
    etl_run_id VARCHAR(50),
    etl_timestamp TIMESTAMP,
    source_table VARCHAR(50)
);
"""


def initialize_staging_schema():
    """Create staging schema and all tables."""
    logger.info("Initializing staging schema...")
    
    try:
        staging_engine = get_staging_engine()
        
        with staging_engine.begin() as conn:
            conn.execute(text(STAGING_SCHEMA_SQL))
        
        logger.info("✅ Staging schema and tables created successfully")
        return True
    
    except Exception as e:
        logger.error(f"❌ Failed to create staging schema: {str(e)}", exc_info=True)
        return False


def initialize_database():
    """
    Main entry point: Initialize all database schemas and tables.
    
    Returns:
        bool: True if successful, False otherwise
    """
    logger.info("=" * 70)
    logger.info("STARTING DATABASE SCHEMA INITIALIZATION")
    logger.info("=" * 70)
    
    try:
        # Create staging schema
        if not initialize_staging_schema():
            return False
        
        # Note: Analytics schema is created by Phase 2 (01_ANALYTICS_SCHEMA.sql)
        logger.info("✅ Database initialization complete!")
        logger.info("=" * 70)
        logger.info("Next steps:")
        logger.info("1. Run Phase 1: python3 -c \"from etl.extract_etl import run_etl; run_etl()\"")
        logger.info("2. Run Phase 2: psql -f analytics/schema/01_ANALYTICS_SCHEMA.sql")
        logger.info("3. Run Phase 3: python3 -c \"from etl.transform_pnl import run_pnl_transformation; run_pnl_transformation()\"")
        logger.info("=" * 70)
        
        return True
    
    except Exception as e:
        logger.error(f"❌ Unexpected error during initialization: {str(e)}", exc_info=True)
        return False


if __name__ == '__main__':
    success = initialize_database()
    sys.exit(0 if success else 1)
