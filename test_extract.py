#!/usr/bin/env python
"""
Standalone test script for Extract module

Run this to test extraction without Airflow:
    python test_extract.py

Set environment variables before running:
    $env:DB_HOST = "localhost"
    $env:DB_PORT = "8100"
    $env:DB_NAME = "paysprint"
    $env:DB_ANALYTICS_NAME = "paysprint_analytics"
    $env:DB_USER = "paysprint"
    $env:DB_PASSWORD = "your_password"
    $env:ENVIRONMENT = "local"
"""

import logging
import sys
from datetime import datetime

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
)
logger = logging.getLogger(__name__)

# Import ETL modules
try:
    from analytics.etl.extract import run_extraction
    from analytics.etl.config import (
        OLTP_DB_CONFIG,
        STAGING_DB_CONFIG,
        ENVIRONMENT,
    )
except ImportError as e:
    logger.error(f"Failed to import ETL modules: {e}")
    logger.info("Make sure you're running from the project root: python test_extract.py")
    sys.exit(1)


def test_database_connections():
    """Test if database connections work"""
    logger.info("=" * 60)
    logger.info("TESTING DATABASE CONNECTIONS")
    logger.info("=" * 60)
    
    logger.info(f"Environment: {ENVIRONMENT}")
    logger.info(f"OLTP Config: {OLTP_DB_CONFIG}")
    logger.info(f"Staging Config: {STAGING_DB_CONFIG}")
    
    try:
        from analytics.etl.config import get_oltp_engine, get_staging_engine
        
        # Test OLTP connection
        logger.info("\nTesting OLTP connection...")
        oltp_engine = get_oltp_engine()
        with oltp_engine.connect() as conn:
            result = conn.execute("SELECT 1")
            logger.info("✓ OLTP connection successful")
        
        # Test Staging connection
        logger.info("Testing Staging connection...")
        staging_engine = get_staging_engine()
        with staging_engine.connect() as conn:
            result = conn.execute("SELECT 1")
            logger.info("✓ Staging connection successful")
        
        return True
    except Exception as e:
        logger.error(f"✗ Database connection failed: {e}")
        return False


def test_extract():
    """Run extraction and report results"""
    logger.info("\n" + "=" * 60)
    logger.info("RUNNING EXTRACTION TEST")
    logger.info("=" * 60)
    
    etl_run_id = f"test_extract_{datetime.utcnow().isoformat()}"
    
    try:
        logger.info(f"ETL Run ID: {etl_run_id}\n")
        
        # Run extraction
        stats = run_extraction(etl_run_id)
        
        logger.info("\n" + "=" * 60)
        logger.info("EXTRACTION RESULTS")
        logger.info("=" * 60)
        
        for table_name, (extracted, loaded) in stats.items():
            status = "✓" if extracted == loaded else "⚠"
            logger.info(f"{status} {table_name}: extracted={extracted}, loaded={loaded}")
        
        return True
    except Exception as e:
        logger.error(f"\n✗ Extraction failed: {e}", exc_info=True)
        return False


def main():
    """Main test runner"""
    logger.info("\n" + "=" * 80)
    logger.info("ANALYTICS ETL - EXTRACT MODULE TEST")
    logger.info("=" * 80)
    
    # Test connections first
    if not test_database_connections():
        logger.error("Database connections failed. Check your environment variables.")
        return 1
    
    # Run extraction
    if not test_extract():
        logger.error("Extraction test failed.")
        return 1
    
    logger.info("\n" + "=" * 60)
    logger.info("✓ ALL TESTS PASSED")
    logger.info("=" * 60)
    return 0


if __name__ == "__main__":
    sys.exit(main())
