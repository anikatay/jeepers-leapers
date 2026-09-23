"""
Extract Module

Extracts data from OLTP database and loads into staging schema
Uses SQLAlchemy for database operations
"""

import logging
from datetime import datetime, timedelta
from typing import Dict, Tuple
import pandas as pd
from sqlalchemy import text, inspect

from .config import (
    get_oltp_engine,
    get_staging_engine,
    ETL_START_DATE,
    ETL_LOOKBACK_DAYS,
    BATCH_SIZE,
    MAX_RETRIES,
    RETRY_DELAY_SECONDS,
)

logger = logging.getLogger(__name__)


class DataExtractor:
    """Extract data from OLTP database into staging schema"""

    def __init__(self, etl_run_id: str):
        """Initialize extractor with ETL run ID for tracking"""
        self.etl_run_id = etl_run_id
        self.oltp_engine = get_oltp_engine()
        self.staging_engine = get_staging_engine()
        self.extract_timestamp = datetime.utcnow()
        self.extraction_stats = {}

    def extract_all_data(self) -> Dict[str, Tuple[int, int]]:
        """
        Extract all required data from OLTP to staging

        Returns:
            Dictionary with table names and (rows_extracted, rows_loaded) tuples
        """
        logger.info(f"Starting data extraction (ETL Run: {self.etl_run_id})")

        try:
            # Clear staging tables first (keep DLQ)
            self._clear_staging_tables()

            # Extract data in sequence (dependencies)
            self.extraction_stats["exchanges"] = self._extract_exchanges()
            self.extraction_stats["accounts"] = self._extract_accounts()
            self.extraction_stats["instruments"] = self._extract_instruments()
            self.extraction_stats["holdings"] = self._extract_holdings()
            self.extraction_stats["trades"] = self._extract_trades()

            logger.info(
                f"Data extraction completed. Stats: {self.extraction_stats}"
            )
            return self.extraction_stats

        except Exception as e:
            logger.error(f"Data extraction failed: {str(e)}", exc_info=True)
            raise

    def _clear_staging_tables(self) -> None:
        """Clear staging tables from previous run"""
        logger.info("Clearing staging tables from previous run")

        tables_to_clear = [
            "staging.trades_raw",
            "staging.holdings_raw",
            "staging.accounts_raw",
            "staging.instruments_raw",
            "staging.exchanges_raw",
        ]

        with self.staging_engine.connect() as conn:
            for table in tables_to_clear:
                try:
                    conn.execute(text(f"TRUNCATE TABLE {table} CASCADE"))
                    conn.commit()
                    logger.debug(f"Cleared {table}")
                except Exception as e:
                    logger.warning(f"Could not clear {table}: {str(e)}")
                    conn.rollback()

    def _extract_exchanges(self) -> Tuple[int, int]:
        """Extract exchanges from OLTP"""
        logger.info("Extracting exchanges data")

        query = """
        SELECT
            exchange_id,
            exchange_name,
            region,
            timezone,
            currency,
            created_at
        FROM public.exchanges
        ORDER BY exchange_id
        """

        return self._extract_and_load(
            query, "staging.exchanges_raw", "Exchange"
        )

    def _extract_accounts(self) -> Tuple[int, int]:
        """Extract accounts from OLTP"""
        logger.info("Extracting accounts data")

        query = """
        SELECT
            account_id,
            user_id,
            status,
            currency,
            balance,
            created_at,
            updated_at
        FROM public.accounts
        ORDER BY account_id
        """

        return self._extract_and_load(query, "staging.accounts_raw", "Account")

    def _extract_instruments(self) -> Tuple[int, int]:
        """Extract instruments from OLTP"""
        logger.info("Extracting instruments data")

        query = """
        SELECT
            instrument_id,
            ticker,
            ticker AS instrument_name,  -- Use ticker as name (can be enhanced)
            exchange_id,
            current_price,
            updated_at AS price_updated_at,
            created_at,
            updated_at
        FROM public.instruments
        ORDER BY instrument_id
        """

        return self._extract_and_load(
            query, "staging.instruments_raw", "Instrument"
        )

    def _extract_holdings(self) -> Tuple[int, int]:
        """Extract holdings from OLTP"""
        logger.info("Extracting holdings data")

        query = """
        SELECT
            account_id,
            instrument_id,
            quantity
        FROM public.holdings
        ORDER BY account_id, instrument_id
        """

        return self._extract_and_load(query, "staging.holdings_raw", "Holding")

    def _extract_trades(self) -> Tuple[int, int]:
        """Extract trades from OLTP (with date filter for incremental-readiness)"""
        logger.info("Extracting trades data")

        # Calculate lookback window for incremental-ready design
        end_date = datetime.utcnow().date()
        start_date = end_date - timedelta(days=ETL_LOOKBACK_DAYS)

        query = """
        SELECT
            trade_id,
            account_id,
            instrument_id,
            side,
            quantity,
            execution_price,
            trade_value,
            executed_at
        FROM public.trades
        WHERE DATE(executed_at) >= :start_date
            AND DATE(executed_at) <= :end_date
        ORDER BY executed_at DESC
        """

        params = {"start_date": start_date, "end_date": end_date}

        return self._extract_and_load(
            query, "staging.trades_raw", "Trade", params=params
        )

    def _extract_and_load(
        self,
        query: str,
        staging_table: str,
        entity_type: str,
        params: Dict = None,
        chunksize: int = BATCH_SIZE,
    ) -> Tuple[int, int]:
        """
        Generic extract and load function

        Args:
            query: SQL query to extract data
            staging_table: Target staging table
            entity_type: Type of entity (for logging)
            params: Query parameters (as dict for named parameters)
            chunksize: Rows per batch

        Returns:
            (rows_extracted, rows_loaded)
        """
        rows_extracted = 0
        rows_loaded = 0

        try:
            # Extract data using pandas (handles connection automatically)
            logger.debug(f"Executing query for {entity_type}")

            if params:
                # Use SQLAlchemy text() for parameterized queries
                df = pd.read_sql(text(query), self.oltp_engine, params=params)
            else:
                df = pd.read_sql(query, self.oltp_engine)

            rows_extracted = len(df)
            logger.info(f"Extracted {rows_extracted} {entity_type} rows")

            if rows_extracted == 0:
                logger.warning(f"No {entity_type} rows extracted")
                return rows_extracted, rows_loaded

            # Add ETL metadata
            df["etl_run_id"] = self.etl_run_id

            # Load to staging in batches
            for i in range(0, len(df), chunksize):
                batch = df.iloc[i : i + chunksize]
                try:
                    batch.to_sql(
                        staging_table.split(".")[1],  # Table name only
                        self.staging_engine,
                        schema=staging_table.split(".")[0],  # Schema
                        if_exists="append",
                        index=False,
                    )
                    rows_loaded += len(batch)
                    logger.debug(
                        f"Loaded batch {i//chunksize + 1} ({len(batch)} rows)"
                    )
                except Exception as e:
                    logger.error(
                        f"Error loading batch to {staging_table}: {str(e)}",
                        exc_info=True,
                    )
                    raise

            logger.info(
                f"Successfully loaded {rows_loaded} {entity_type} rows to {staging_table}"
            )
            return rows_extracted, rows_loaded

        except Exception as e:
            logger.error(
                f"Error extracting {entity_type}: {str(e)}", exc_info=True
            )
            raise

    def get_extraction_stats(self) -> Dict[str, Tuple[int, int]]:
        """Return extraction statistics"""
        return self.extraction_stats


def run_extraction(etl_run_id: str) -> Dict[str, Tuple[int, int]]:
    """
    Main entry point for extraction

    Args:
        etl_run_id: Airflow DAG run ID for tracking

    Returns:
        Dictionary with extraction statistics
    """
    logger.info(f"=== DATA EXTRACTION STARTED (Run: {etl_run_id}) ===")

    try:
        extractor = DataExtractor(etl_run_id)
        stats = extractor.extract_all_data()

        logger.info(f"=== DATA EXTRACTION COMPLETED ===")
        logger.info(f"Extraction Summary: {stats}")

        return stats

    except Exception as e:
        logger.error(f"=== DATA EXTRACTION FAILED ===", exc_info=True)
        raise


if __name__ == "__main__":
    # For local testing
    import sys

    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
    )

    test_run_id = f"test_extract_{datetime.utcnow().isoformat()}"
    stats = run_extraction(test_run_id)
    print("\n" + "=" * 60)
    print("Extraction Complete")
    print("=" * 60)
    for table, (extracted, loaded) in stats.items():
        print(f"{table:20} | Extracted: {extracted:8} | Loaded: {loaded:8}")
