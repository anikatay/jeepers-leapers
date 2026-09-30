"""
Extract ETL Module

Extracts data from OLTP database (paysprint.public) and loads into staging schema.
Runs once per hour at :00 - all downstream ETLs reuse this data.

This module provides a DataExtractor class that:
- Extracts tables: exchanges, accounts, instruments, holdings, trades
- Adds metadata columns: etl_run_id, etl_timestamp, source_table
- Loads to paysprint_analytics.staging schema
- Tracks extraction/load statistics per table

Can be run standalone or imported by other ETL modules.

Example usage:
    from analytics.etl.extract_etl import DataExtractor
    extractor = DataExtractor('my_etl_run_001')
    stats = extractor.extract_all_data()
    print(stats)  # {'exchanges': (2, 2), 'accounts': (5, 5), ...}
    
    OR run standalone:
    python analytics/etl/extract_etl.py
"""

import logging
import os
import sys
import uuid
from datetime import datetime
from typing import Dict, Tuple, Optional

import pandas as pd
from sqlalchemy import create_engine, text, inspect

from .config import (
    get_oltp_engine,
    get_staging_engine,
    ETL_LOOKBACK_DAYS,
    BATCH_SIZE,
    validate_config,
)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s',
    handlers=[
        logging.StreamHandler(sys.stderr),
        logging.FileHandler('etl.log', mode='a')
    ]
)
logger = logging.getLogger(__name__)


class DataExtractor:
    """Extract data from OLTP database into staging schema."""
    
    def __init__(self, etl_run_id: str):
        """
        Initialize extractor with ETL run ID for tracking.
        
        Args:
            etl_run_id: Unique identifier for this ETL run (for traceability)
        """
        self.etl_run_id = etl_run_id
        self.oltp_engine = get_oltp_engine()
        self.staging_engine = get_staging_engine()
        self.extract_timestamp = datetime.utcnow()
        self.extraction_stats = {}
        
        logger.info(f"DataExtractor initialized with run_id: {self.etl_run_id}")
    
    def extract_all_data(self) -> Dict[str, Tuple[int, int]]:
        """
        Extract all required data from OLTP to staging.
        
        Returns:
            Dictionary mapping table names to (rows_extracted, rows_loaded) tuples
            Example: {'exchanges': (2, 2), 'accounts': (5, 5), 'instruments': (7, 7), ...}
        
        Raises:
            Exception: If critical error occurs that prevents all extractions
        """
        logger.info(f"Starting data extraction (ETL Run: {self.etl_run_id})")
        
        try:
            # Clear staging tables first (keep schema intact, just truncate data)
            self._clear_staging_tables()
            
            # Extract data in sequence (maintains dependencies)
            # Note: Order matters - dimensions before facts
            self.extraction_stats["exchanges"] = self._extract_exchanges()
            self.extraction_stats["accounts"] = self._extract_accounts()
            self.extraction_stats["instruments"] = self._extract_instruments()
            self.extraction_stats["holdings"] = self._extract_holdings()
            self.extraction_stats["trades"] = self._extract_trades()
            
            logger.info(f"Data extraction completed. Stats: {self.extraction_stats}")
            return self.extraction_stats
        
        except Exception as e:
            logger.error(f"Data extraction failed: {str(e)}", exc_info=True)
            raise
    
    def _clear_staging_tables(self) -> None:
        """
        Clear staging tables from previous run.
        Uses DROP TABLE IF EXISTS (compatible with older PostgreSQL versions).
        """
        logger.info("Clearing staging tables from previous run")
        
        tables_to_clear = [
            "staging.trades_raw",
            "staging.holdings_raw",
            "staging.accounts_raw",
            "staging.instruments_raw",
            "staging.exchanges_raw",
        ]
        
        with self.staging_engine.begin() as conn:
            # First ensure staging schema exists
            conn.execute(text("CREATE SCHEMA IF NOT EXISTS staging"))
            
            # Then drop each table if it exists (recreates schema on load)
            for table in tables_to_clear:
                try:
                    conn.execute(text(f"DROP TABLE IF EXISTS {table} CASCADE"))
                    logger.debug(f"Dropped {table}")
                except Exception as e:
                    logger.warning(f"Could not drop {table}: {str(e)}")
    
    def _extract_exchanges(self) -> Tuple[int, int]:
        """
        Extract exchanges from OLTP.
        
        Returns:
            (rows_extracted, rows_loaded) tuple
        """
        logger.info("Extracting exchanges data")
        
        query = """
        SELECT
            exchange_id,
            name,
            region,
            timezone,
            currency
        FROM public.exchanges
        """
        
        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            rows_extracted = len(df)
            
            # Add metadata
            df["etl_run_id"] = self.etl_run_id
            df["etl_timestamp"] = self.extract_timestamp
            df["source_table"] = "exchanges"
            
            rows_loaded = self._load_to_staging(df, "exchanges_raw")
            logger.info(f"Extracted {rows_extracted} exchanges, loaded {rows_loaded}")
            
            return (rows_extracted, rows_loaded)
        
        except Exception as e:
            logger.error(f"Error extracting exchanges: {str(e)}", exc_info=True)
            return (0, 0)
    
    def _extract_accounts(self) -> Tuple[int, int]:
        """
        Extract accounts from OLTP.
        
        Returns:
            (rows_extracted, rows_loaded) tuple
        """
        logger.info("Extracting accounts data")
        
        query = """
        SELECT
            account_id,
            user_id,
            currency,
            balance,
            status,
            created_at
        FROM public.accounts
        """
        
        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            rows_extracted = len(df)
            
            # Add metadata
            df["etl_run_id"] = self.etl_run_id
            df["etl_timestamp"] = self.extract_timestamp
            df["source_table"] = "accounts"
            
            rows_loaded = self._load_to_staging(df, "accounts_raw")
            logger.info(f"Extracted {rows_extracted} accounts, loaded {rows_loaded}")
            
            return (rows_extracted, rows_loaded)
        
        except Exception as e:
            logger.error(f"Error extracting accounts: {str(e)}", exc_info=True)
            return (0, 0)
    
    def _extract_instruments(self) -> Tuple[int, int]:
        """
        Extract instruments from OLTP.
        
        Returns:
            (rows_extracted, rows_loaded) tuple
        """
        logger.info("Extracting instruments data")
        
        query = """
        SELECT
            instrument_id,
            ticker,
            name,
            exchange_id,
            current_price,
            updated_at
        FROM public.instruments
        """
        
        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            rows_extracted = len(df)
            
            # Add metadata
            df["etl_run_id"] = self.etl_run_id
            df["etl_timestamp"] = self.extract_timestamp
            df["source_table"] = "instruments"
            
            rows_loaded = self._load_to_staging(df, "instruments_raw")
            logger.info(f"Extracted {rows_extracted} instruments, loaded {rows_loaded}")
            
            return (rows_extracted, rows_loaded)
        
        except Exception as e:
            logger.error(f"Error extracting instruments: {str(e)}", exc_info=True)
            return (0, 0)
    
    def _extract_holdings(self) -> Tuple[int, int]:
        """
        Extract holdings (account positions) from OLTP.
        
        Returns:
            (rows_extracted, rows_loaded) tuple
        """
        logger.info("Extracting holdings data")
        
        query = """
        SELECT
            account_id,
            instrument_id,
            quantity
        FROM public.holdings
        """
        
        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            rows_extracted = len(df)
            
            # Add metadata
            df["etl_run_id"] = self.etl_run_id
            df["etl_timestamp"] = self.extract_timestamp
            df["source_table"] = "holdings"
            
            rows_loaded = self._load_to_staging(df, "holdings_raw")
            logger.info(f"Extracted {rows_extracted} holdings, loaded {rows_loaded}")
            
            return (rows_extracted, rows_loaded)
        
        except Exception as e:
            logger.error(f"Error extracting holdings: {str(e)}", exc_info=True)
            return (0, 0)
    
    def _extract_trades(self, days: int = None) -> Tuple[int, int]:
        """
        Extract trades from OLTP (last N days).
        
        Args:
            days: Number of days to look back (default: ETL_LOOKBACK_DAYS from config)
        
        Returns:
            (rows_extracted, rows_loaded) tuple
        """
        if days is None:
            days = ETL_LOOKBACK_DAYS
        
        logger.info(f"Extracting trades from last {days} days")
        
        query = f"""
        SELECT
            trade_id,
            account_id,
            instrument_id,
            side,
            quantity,
            execution_price,
            executed_at
        FROM public.trades
        WHERE executed_at >= NOW() - INTERVAL '{days} days'
        """
        
        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            rows_extracted = len(df)
            
            # Add metadata
            df["etl_run_id"] = self.etl_run_id
            df["etl_timestamp"] = self.extract_timestamp
            df["source_table"] = "trades"
            
            rows_loaded = self._load_to_staging(df, "trades_raw")
            logger.info(f"Extracted {rows_extracted} trades, loaded {rows_loaded}")
            
            return (rows_extracted, rows_loaded)
        
        except Exception as e:
            logger.error(f"Error extracting trades: {str(e)}", exc_info=True)
            return (0, 0)
    
    def _load_to_staging(self, df: pd.DataFrame, table_name: str) -> int:
        """
        Load DataFrame to staging table.
        
        Args:
            df: DataFrame to load
            table_name: Target table name in staging schema
        
        Returns:
            Number of rows loaded (0 if empty or error)
        """
        if df is None or df.empty:
            logger.warning(f"Skipping empty DataFrame for table: staging.{table_name}")
            return 0
        
        try:
            rows_count = len(df)
            logger.debug(f"Loading {rows_count} rows to staging.{table_name}...")
            
            # Use pandas to_sql with append mode (creates table if not exists)
            df.to_sql(
                name=table_name,
                con=self.staging_engine,
                schema='staging',
                if_exists='append',  # Append to existing table (truncated above)
                index=False,
                method='multi',  # Use multi-insert for faster loading
                chunksize=BATCH_SIZE
            )
            
            logger.debug(f"Successfully loaded {rows_count} rows to staging.{table_name}")
            return rows_count
        
        except Exception as e:
            logger.error(f"Failed to load data to staging.{table_name}: {str(e)}", exc_info=True)
            return 0


def run_etl() -> Dict[str, any]:
    """
    Execute the complete extraction ETL pipeline.
    
    Returns:
        Dictionary with run statistics:
        {
            "status": "success|partial|failed",
            "run_id": "<uuid>",
            "extraction_stats": {
                "exchanges": (rows_extracted, rows_loaded),
                "accounts": (rows_extracted, rows_loaded),
                ...
            }
        }
    """
    # Validate configuration first
    if not validate_config():
        logger.error("Configuration validation failed. Check .env file.")
        return {
            "status": "failed",
            "run_id": None,
            "extraction_stats": {}
        }
    
    # Generate unique run ID
    run_id = str(uuid.uuid4())
    logger.info("=" * 70)
    logger.info(f"STARTING ETL RUN: {run_id}")
    logger.info("=" * 70)
    
    try:
        # Create extractor and run
        extractor = DataExtractor(run_id)
        stats = extractor.extract_all_data()
        
        # Determine status based on results
        total_extracted = sum(row_count[0] for row_count in stats.values())
        total_loaded = sum(row_count[1] for row_count in stats.values())
        
        if total_loaded > 0:
            status = "success" if total_extracted == total_loaded else "partial"
        else:
            status = "failed"
        
        result = {
            "status": status,
            "run_id": run_id,
            "extraction_stats": stats
        }
        
        logger.info("=" * 70)
        logger.info(f"ETL RUN COMPLETED: {status.upper()}")
        logger.info(f"Summary: {result}")
        logger.info("=" * 70)
        
        return result
    
    except Exception as e:
        logger.error(f"Unexpected error in ETL pipeline: {str(e)}", exc_info=True)
        return {
            "status": "failed",
            "run_id": run_id,
            "extraction_stats": {}
        }


if __name__ == '__main__':
    """Run ETL when executed directly"""
    result = run_etl()
    sys.exit(0 if result["status"] in ["success", "partial"] else 1)
