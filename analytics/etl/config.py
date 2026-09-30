"""
ETL Configuration Module

Centralized database connections, constants, and settings for all ETL modules.

Environment Variables Expected:
  - PAYSPRINT_SOURCE_DB_URL: PostgreSQL connection string for OLTP (paysprint)
  - PAYSPRINT_ANALYTICS_DB_URL: PostgreSQL connection string for analytics (paysprint_analytics)
  
Example:
  PAYSPRINT_SOURCE_DB_URL=postgresql://user:password@host:port/paysprint
  PAYSPRINT_ANALYTICS_DB_URL=postgresql://user:password@host:port/paysprint_analytics
"""

import os
import logging
from dotenv import load_dotenv
from sqlalchemy import create_engine

# Load environment variables from .env file
load_dotenv()

logger = logging.getLogger(__name__)

# ============================================================================
# DATABASE CONNECTIONS
# ============================================================================

# OLTP Database (source of truth - paysprint production database)
PAYSPRINT_SOURCE_DB_URL = os.getenv("PAYSPRINT_SOURCE_DB_URL")

# Analytics Database (target - paysprint_analytics for staging + analytics)
PAYSPRINT_ANALYTICS_DB_URL = os.getenv("PAYSPRINT_ANALYTICS_DB_URL")


def validate_config() -> bool:
    """
    Validate that all required environment variables are set.
    
    Returns:
        True if all required vars present, False otherwise
    """
    required_vars = {
        "PAYSPRINT_SOURCE_DB_URL": PAYSPRINT_SOURCE_DB_URL,
        "PAYSPRINT_ANALYTICS_DB_URL": PAYSPRINT_ANALYTICS_DB_URL,
    }
    
    missing_vars = [var for var, value in required_vars.items() if not value]
    
    if missing_vars:
        logger.error(f"Missing required environment variables: {', '.join(missing_vars)}")
        logger.error("Please check your .env file. See .env.example for template.")
        return False
    
    return True


def get_oltp_engine():
    """
    Create and return SQLAlchemy engine for OLTP database (paysprint).
    
    Returns:
        SQLAlchemy Engine configured for OLTP
    """
    if not PAYSPRINT_SOURCE_DB_URL:
        raise ValueError("PAYSPRINT_SOURCE_DB_URL not set in environment")
    
    return create_engine(
        PAYSPRINT_SOURCE_DB_URL,
        echo=False,
        pool_pre_ping=True,  # Test connection before using
        pool_size=10,
        max_overflow=20,
    )


def get_staging_engine():
    """
    Create and return SQLAlchemy engine for staging database (paysprint_analytics).
    Uses same connection as analytics (staging is a schema in same database).
    
    Returns:
        SQLAlchemy Engine configured for staging
    """
    if not PAYSPRINT_ANALYTICS_DB_URL:
        raise ValueError("PAYSPRINT_ANALYTICS_DB_URL not set in environment")
    
    return create_engine(
        PAYSPRINT_ANALYTICS_DB_URL,
        echo=False,
        pool_pre_ping=True,
        pool_size=10,
        max_overflow=20,
    )


def get_analytics_engine():
    """
    Create and return SQLAlchemy engine for analytics database.
    Uses same connection as staging (analytics is a schema in same database).
    
    Returns:
        SQLAlchemy Engine configured for analytics
    """
    if not PAYSPRINT_ANALYTICS_DB_URL:
        raise ValueError("PAYSPRINT_ANALYTICS_DB_URL not set in environment")
    
    return create_engine(
        PAYSPRINT_ANALYTICS_DB_URL,
        echo=False,
        pool_pre_ping=True,
        pool_size=10,
        max_overflow=20,
    )


# ============================================================================
# ETL CONFIGURATION CONSTANTS
# ============================================================================

# Date range for data extraction
ETL_LOOKBACK_DAYS = int(os.getenv("ETL_LOOKBACK_DAYS", "90"))

# Batch processing settings
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "1000"))
MAX_RETRIES = int(os.getenv("MAX_RETRIES", "3"))
RETRY_DELAY_SECONDS = int(os.getenv("RETRY_DELAY_SECONDS", "5"))

# ============================================================================
# DATA QUALITY THRESHOLDS
# ============================================================================

# Price validation (for data quality checks in future modules)
PRICE_MIN = 0.01
PRICE_MAX = 1_000_000

# Quantity validation (for data quality checks in future modules)
QUANTITY_MIN = 0.0001  # Fractional shares allowed
QUANTITY_MAX = 1_000_000

# Date validation (for data quality checks in future modules)
DATE_FUTURE_TOLERANCE_DAYS = 1  # Allow trades up to 1 day in future

# Row count reconciliation tolerance (%) - used in validation module
ROW_COUNT_TOLERANCE = 5.0

# ============================================================================
# LOGGING
# ============================================================================

LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
LOG_FILE = os.getenv("LOG_FILE", "analytics/etl/etl.log")

# ============================================================================
# SANITY CHECK
# ============================================================================

if __name__ == "__main__":
    """Test config module when run directly"""
    print("Testing ETL Configuration...")
    
    if validate_config():
        print("✓ All required environment variables are set")
        print(f"  - PAYSPRINT_SOURCE_DB_URL: {PAYSPRINT_SOURCE_DB_URL[:50]}...")
        print(f"  - PAYSPRINT_ANALYTICS_DB_URL: {PAYSPRINT_ANALYTICS_DB_URL[:50]}...")
        
        try:
            oltp_engine = get_oltp_engine()
            with oltp_engine.connect() as conn:
                conn.execute("SELECT 1")
            print("✓ OLTP database connection successful")
        except Exception as e:
            print(f"✗ OLTP database connection failed: {e}")
        
        try:
            staging_engine = get_staging_engine()
            with staging_engine.connect() as conn:
                conn.execute("SELECT 1")
            print("✓ Analytics/Staging database connection successful")
        except Exception as e:
            print(f"✗ Analytics/Staging database connection failed: {e}")
    else:
        print("✗ Configuration validation failed. Check .env file.")
