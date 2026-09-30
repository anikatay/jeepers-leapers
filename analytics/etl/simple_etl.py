"""
Simple ETL Script: Extract → Transform → Load

Extracts data from paysprint.public (exchanges, accounts, instruments, trades),
adds metadata columns (etl_run_id, etl_timestamp, source_table),
and loads to paysprint_analytics.staging.

This script is designed to be:
1. Runnable standalone: python analytics/etl/simple_etl.py
2. Importable by Airflow: from analytics.etl.simple_etl import run_etl
3. Graceful on errors: logs issues and continues where possible

Usage:
    python analytics/etl/simple_etl.py
    
    OR
    
    from analytics.etl.simple_etl import run_etl
    result = run_etl()
    print(result)
"""

import logging
import os
import sys
import uuid
from datetime import datetime, timedelta
from typing import Dict, Any, Optional

import pandas as pd
from sqlalchemy import create_engine, text, inspect
from dotenv import load_dotenv

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='[%(asctime)s] [%(levelname)s] [%(name)s] %(message)s',
    handlers=[
        logging.StreamHandler(sys.stderr),
        logging.FileHandler('analytics/etl/etl.log', mode='a')
    ]
)
logger = logging.getLogger(__name__)


class ETLConfig:
    """Configuration for ETL pipeline."""
    
    def __init__(self):
        load_dotenv()
        self.source_db_url = os.getenv('PAYSPRINT_SOURCE_DB_URL')
        self.analytics_db_url = os.getenv('PAYSPRINT_ANALYTICS_DB_URL')
        
        if not self.source_db_url:
            logger.warning("PAYSPRINT_SOURCE_DB_URL not set in environment")
        if not self.analytics_db_url:
            logger.warning("PAYSPRINT_ANALYTICS_DB_URL not set in environment")
    
    def validate(self) -> bool:
        """Validate configuration."""
        if not self.source_db_url or not self.analytics_db_url:
            logger.error("Missing required environment variables. Check .env file.")
            return False
        return True


class ETLExtractor:
    """Extract data from paysprint.public schema."""
    
    def __init__(self, source_db_url: str):
        self.source_db_url = source_db_url
        self.engine = None
    
    def connect(self) -> bool:
        """Establish connection to source database."""
        try:
            logger.info("Connecting to paysprint (source) database...")
            self.engine = create_engine(self.source_db_url, echo=False)
            
            # Test connection
            with self.engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            
            logger.info("Source database connection successful")
            return True
        except Exception as e:
            logger.error(f"Failed to connect to source database: {e}")
            return False
    
    def extract_exchanges(self) -> Optional[pd.DataFrame]:
        """Extract exchanges from paysprint.public.exchanges."""
        try:
            logger.info("Extracting exchanges...")
            query = "SELECT * FROM paysprint.public.exchanges"
            df = pd.read_sql(query, self.engine)
            logger.info(f"Extracted {len(df)} exchanges")
            return df
        except Exception as e:
            logger.error(f"Failed to extract exchanges: {e}")
            return None
    
    def extract_accounts(self) -> Optional[pd.DataFrame]:
        """Extract accounts from paysprint.public.accounts."""
        try:
            logger.info("Extracting accounts...")
            query = "SELECT * FROM paysprint.public.accounts"
            df = pd.read_sql(query, self.engine)
            logger.info(f"Extracted {len(df)} accounts")
            return df
        except Exception as e:
            logger.error(f"Failed to extract accounts: {e}")
            return None
    
    def extract_instruments(self) -> Optional[pd.DataFrame]:
        """Extract instruments from paysprint.public.instruments."""
        try:
            logger.info("Extracting instruments...")
            query = "SELECT * FROM paysprint.public.instruments"
            df = pd.read_sql(query, self.engine)
            logger.info(f"Extracted {len(df)} instruments")
            return df
        except Exception as e:
            logger.error(f"Failed to extract instruments: {e}")
            return None
    
    def extract_trades(self, days: int = 90) -> Optional[pd.DataFrame]:
        """Extract trades from last N days."""
        try:
            logger.info(f"Extracting trades from last {days} days...")
            query = f"""
                SELECT * FROM paysprint.public.trades
                WHERE executed_at >= NOW() - INTERVAL '{days} days'
            """
            df = pd.read_sql(query, self.engine)
            logger.info(f"Extracted {len(df)} trades from last {days} days")
            return df
        except Exception as e:
            logger.error(f"Failed to extract trades: {e}")
            return None
    
    def close(self):
        """Close database connection."""
        if self.engine:
            self.engine.dispose()
            logger.info("Source database connection closed")


class ETLTransformer:
    """Transform data with ETL metadata."""
    
    def __init__(self, run_id: str):
        self.run_id = run_id
        self.etl_timestamp = datetime.utcnow()
    
    def transform(self, df: pd.DataFrame, source_table: str) -> pd.DataFrame:
        """
        Add metadata columns to DataFrame.
        
        Args:
            df: Input DataFrame
            source_table: Name of source table
        
        Returns:
            DataFrame with added columns: etl_run_id, etl_timestamp, source_table
        """
        if df is None or df.empty:
            logger.warning(f"Empty DataFrame for {source_table}, skipping transform")
            return None
        
        try:
            df_transformed = df.copy()
            df_transformed['etl_run_id'] = self.run_id
            df_transformed['etl_timestamp'] = self.etl_timestamp
            df_transformed['source_table'] = source_table
            
            logger.info(f"Transformed {source_table}: added 3 metadata columns")
            return df_transformed
        except Exception as e:
            logger.error(f"Failed to transform {source_table}: {e}")
            return None


class ETLLoader:
    """Load data to paysprint_analytics.staging schema."""
    
    def __init__(self, analytics_db_url: str):
        self.analytics_db_url = analytics_db_url
        self.engine = None
    
    def connect(self) -> bool:
        """Establish connection to analytics database."""
        try:
            logger.info("Connecting to paysprint_analytics database...")
            self.engine = create_engine(self.analytics_db_url, echo=False)
            
            # Test connection
            with self.engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            
            logger.info("Analytics database connection successful")
            return True
        except Exception as e:
            logger.error(f"Failed to connect to analytics database: {e}")
            return False
    
    def ensure_staging_schema(self) -> bool:
        """Ensure staging schema exists."""
        try:
            with self.engine.connect() as conn:
                conn.execute(text("CREATE SCHEMA IF NOT EXISTS staging"))
                # Drop old staging tables if they exist (to recreate with correct schema)
                conn.execute(text("DROP TABLE IF EXISTS staging.exchanges_raw CASCADE"))
                conn.execute(text("DROP TABLE IF EXISTS staging.accounts_raw CASCADE"))
                conn.execute(text("DROP TABLE IF EXISTS staging.instruments_raw CASCADE"))
                conn.execute(text("DROP TABLE IF EXISTS staging.trades_raw CASCADE"))
                conn.commit()
            logger.info("Staging schema is ready (old tables dropped)")
            return True
        except Exception as e:
            logger.error(f"Failed to create staging schema: {e}")
            return False
    
    def load_dataframe(self, df: pd.DataFrame, table_name: str) -> bool:
        """
        Load DataFrame to staging table.
        
        Args:
            df: DataFrame to load
            table_name: Target table name (without schema prefix)
        
        Returns:
            True if successful, False otherwise
        """
        if df is None or df.empty:
            logger.warning(f"Skipping empty DataFrame for table: staging.{table_name}")
            return True
        
        try:
            logger.info(f"Loading {len(df)} rows to staging.{table_name}...")
            
            # Use pandas to_sql with append mode (creates table if not exists)
            df.to_sql(
                name=table_name,
                con=self.engine,
                schema='staging',
                if_exists='append',  # Append to existing table
                index=False,
                method='multi',  # Use multi-insert for faster loading
                chunksize=1000
            )
            
            logger.info(f"Successfully loaded {len(df)} rows to staging.{table_name}")
            return True
        except Exception as e:
            logger.error(f"Failed to load data to staging.{table_name}: {e}")
            return False
    
    def close(self):
        """Close database connection."""
        if self.engine:
            self.engine.dispose()
            logger.info("Analytics database connection closed")


def run_etl() -> Dict[str, Any]:
    """
    Execute the complete ETL pipeline.
    
    Returns:
        Dictionary with status, run_id, and rows_loaded:
        {
            "status": "success|partial|failed",
            "run_id": "<uuid>",
            "rows_loaded": {
                "exchanges": N,
                "accounts": N,
                "instruments": N,
                "trades": N
            }
        }
    """
    # Generate unique run ID
    run_id = str(uuid.uuid4())
    logger.info(f"Starting ETL run: {run_id}")
    
    # Initialize result tracking
    result = {
        "status": "failed",
        "run_id": run_id,
        "rows_loaded": {
            "exchanges": 0,
            "accounts": 0,
            "instruments": 0,
            "trades": 0
        }
    }
    
    # Load configuration
    config = ETLConfig()
    if not config.validate():
        logger.error("Configuration validation failed")
        return result
    
    # Initialize components
    extractor = ETLExtractor(config.source_db_url)
    transformer = ETLTransformer(run_id)
    loader = ETLLoader(config.analytics_db_url)
    
    try:
        # PHASE 1: EXTRACT
        logger.info("=" * 60)
        logger.info("PHASE 1: EXTRACT")
        logger.info("=" * 60)
        
        if not extractor.connect():
            logger.error("Extraction failed: could not connect to source database")
            return result
        
        df_exchanges = extractor.extract_exchanges()
        df_accounts = extractor.extract_accounts()
        df_instruments = extractor.extract_instruments()
        df_trades = extractor.extract_trades(days=90)
        
        extractor.close()
        
        # Check if we got any data
        data_extracted = any([
            df_exchanges is not None and not df_exchanges.empty,
            df_accounts is not None and not df_accounts.empty,
            df_instruments is not None and not df_instruments.empty,
            df_trades is not None and not df_trades.empty
        ])
        
        if not data_extracted:
            logger.error("Extraction failed: no data retrieved from any table")
            return result
        
        # PHASE 2: TRANSFORM
        logger.info("=" * 60)
        logger.info("PHASE 2: TRANSFORM")
        logger.info("=" * 60)
        
        df_exchanges_t = transformer.transform(df_exchanges, "exchanges")
        df_accounts_t = transformer.transform(df_accounts, "accounts")
        df_instruments_t = transformer.transform(df_instruments, "instruments")
        df_trades_t = transformer.transform(df_trades, "trades")
        
        # PHASE 3: LOAD
        logger.info("=" * 60)
        logger.info("PHASE 3: LOAD")
        logger.info("=" * 60)
        
        if not loader.connect():
            logger.error("Loading failed: could not connect to analytics database")
            return result
        
        if not loader.ensure_staging_schema():
            logger.error("Loading failed: could not ensure staging schema")
            loader.close()
            return result
        
        # Load each transformed DataFrame
        load_success_count = 0
        
        if loader.load_dataframe(df_exchanges_t, 'exchanges_raw'):
            result["rows_loaded"]["exchanges"] = len(df_exchanges) if df_exchanges is not None else 0
            load_success_count += 1
        
        if loader.load_dataframe(df_accounts_t, 'accounts_raw'):
            result["rows_loaded"]["accounts"] = len(df_accounts) if df_accounts is not None else 0
            load_success_count += 1
        
        if loader.load_dataframe(df_instruments_t, 'instruments_raw'):
            result["rows_loaded"]["instruments"] = len(df_instruments) if df_instruments is not None else 0
            load_success_count += 1
        
        if loader.load_dataframe(df_trades_t, 'trades_raw'):
            result["rows_loaded"]["trades"] = len(df_trades) if df_trades is not None else 0
            load_success_count += 1
        
        loader.close()
        
        # Determine overall status
        if load_success_count == 4:
            result["status"] = "success"
            logger.info(f"ETL run {run_id} completed successfully")
        elif load_success_count > 0:
            result["status"] = "partial"
            logger.warning(f"ETL run {run_id} completed with partial success ({load_success_count}/4 tables)")
        else:
            result["status"] = "failed"
            logger.error(f"ETL run {run_id} failed: no tables loaded")
        
        logger.info("=" * 60)
        logger.info(f"SUMMARY: {result}")
        logger.info("=" * 60)
        
        return result
    
    except Exception as e:
        logger.error(f"Unexpected error in ETL pipeline: {e}", exc_info=True)
        return result


if __name__ == '__main__':
    result = run_etl()
    sys.exit(0 if result["status"] in ["success", "partial"] else 1)
