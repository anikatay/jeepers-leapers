"""
Extract ETL Module

Extracts data from OLTP database and loads into staging schema.
Runs once per hour (at :00) - all downstream ETLs reuse this data.

Example usage:
    from etl.extract_etl import DataExtractor
    extractor = DataExtractor('airflow_run_id_12345')
    stats = extractor.extract_all_data()
    print(stats)  # {'exchanges': (10, 10), 'accounts': (1000, 1000), ...}
"""

import logging
from datetime import datetime
from typing import Dict, Tuple
import pandas as pd
from sqlalchemy import text

from config import (
    get_oltp_engine,
    get_staging_engine,
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
            Example: {'exchanges': (10, 10), 'accounts': (1000, 1000), ...}
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
            name as exchange_name,
            region,
            timezone,
            currency,
            created_at
        FROM public.exchanges
        """

        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            df["etl_run_id"] = self.etl_run_id

            rows_extracted = len(df)
            rows_loaded = self._load_to_staging(df, "exchanges_raw")

            logger.info(f"Extracted {rows_extracted} exchanges, loaded {rows_loaded}")
            return (rows_extracted, rows_loaded)

        except Exception as e:
            logger.error(f"Error extracting exchanges: {str(e)}", exc_info=True)
            raise

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
            created_at as updated_at
        FROM public.accounts
        """

        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            df["etl_run_id"] = self.etl_run_id

            rows_extracted = len(df)
            rows_loaded = self._load_to_staging(df, "accounts_raw")

            logger.info(f"Extracted {rows_extracted} accounts, loaded {rows_loaded}")
            return (rows_extracted, rows_loaded)

        except Exception as e:
            logger.error(f"Error extracting accounts: {str(e)}", exc_info=True)
            raise

    def _extract_instruments(self) -> Tuple[int, int]:
        """Extract instruments from OLTP"""
        logger.info("Extracting instruments data")

        query = """
        SELECT
            instrument_id,
            ticker,
            name as instrument_name,
            exchange_id,
            current_price,
            updated_at as price_updated_at,
            created_at,
            updated_at
        FROM public.instruments
        """

        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            df["etl_run_id"] = self.etl_run_id

            rows_extracted = len(df)
            rows_loaded = self._load_to_staging(df, "instruments_raw")

            logger.info(f"Extracted {rows_extracted} instruments, loaded {rows_loaded}")
            return (rows_extracted, rows_loaded)

        except Exception as e:
            logger.error(f"Error extracting instruments: {str(e)}", exc_info=True)
            raise

    def _extract_holdings(self) -> Tuple[int, int]:
        """Extract holdings from OLTP"""
        logger.info("Extracting holdings data")

        query = """
        SELECT
            account_id,
            instrument_id,
            quantity,
            created_at
        FROM public.holdings
        """

        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            df["etl_run_id"] = self.etl_run_id

            rows_extracted = len(df)
            rows_loaded = self._load_to_staging(df, "holdings_raw")

            logger.info(f"Extracted {rows_extracted} holdings, loaded {rows_loaded}")
            return (rows_extracted, rows_loaded)

        except Exception as e:
            logger.error(f"Error extracting holdings: {str(e)}", exc_info=True)
            raise

    def _extract_trades(self) -> Tuple[int, int]:
        """Extract trades from OLTP"""
        logger.info("Extracting trades data")

        query = """
        SELECT
            trade_id,
            account_id,
            instrument_id,
            side,
            quantity,
            execution_price,
            trade_value,
            executed_at,
            created_at
        FROM public.trades
        """

        try:
            df = pd.read_sql(text(query), self.oltp_engine)
            df["etl_run_id"] = self.etl_run_id

            rows_extracted = len(df)
            rows_loaded = self._load_to_staging(df, "trades_raw")

            logger.info(f"Extracted {rows_extracted} trades, loaded {rows_loaded}")
            return (rows_extracted, rows_loaded)

        except Exception as e:
            logger.error(f"Error extracting trades: {str(e)}", exc_info=True)
            raise

    def _load_to_staging(self, df: pd.DataFrame, table_name: str) -> int:
        """Load DataFrame to staging table"""
        try:
            rows_loaded = 0
            for i in range(0, len(df), BATCH_SIZE):
                batch = df.iloc[i : i + BATCH_SIZE]
                batch.to_sql(
                    table_name,
                    self.staging_engine,
                    schema="staging",
                    if_exists="append",
                    index=False,
                )
                rows_loaded += len(batch)
                logger.debug(f"Loaded batch {i//BATCH_SIZE + 1} ({len(batch)} rows) to {table_name}")

            return rows_loaded

        except Exception as e:
            logger.error(f"Error loading to {table_name}: {str(e)}", exc_info=True)
            raise


def run_extract(etl_run_id: str) -> Dict[str, Tuple[int, int]]:
    """
    Main entry point for extraction

    Args:
        etl_run_id: Airflow DAG run ID

    Returns:
        Dictionary with extraction statistics
    """
    logger.info(f"=== DATA EXTRACT STARTED (Run: {etl_run_id}) ===")

    try:
        extractor = DataExtractor(etl_run_id)
        stats = extractor.extract_all_data()

        logger.info(f"=== DATA EXTRACT COMPLETED ===")
        logger.info(f"Extract Summary: {stats}")

        return stats

    except Exception as e:
        logger.error(f"=== DATA EXTRACT FAILED ===", exc_info=True)
        raise


if __name__ == "__main__":
    # For local testing
    import sys
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
    )

    run_id = sys.argv[1] if len(sys.argv) > 1 else "local_test_run"
    stats = run_extract(run_id)
    print("Extraction stats:", stats)
