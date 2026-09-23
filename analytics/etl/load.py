"""
Load Module

Loads transformed data into analytics schema using transactions
Ensures all-or-nothing semantics and data integrity
"""

import logging
from datetime import datetime
from typing import Dict, Tuple
import pandas as pd
from sqlalchemy import text, exc

from .config import get_olap_engine, BATCH_SIZE

logger = logging.getLogger(__name__)


class DataLoader:
    """Load transformed data into analytics schema (OLAP)"""

    def __init__(self, etl_run_id: str):
        """Initialize loader"""
        self.etl_run_id = etl_run_id
        self.olap_engine = get_olap_engine()
        self.load_stats = {}

    def load_all_data(self, transformed_data: Dict[str, pd.DataFrame]) -> Dict[str, int]:
        """
        Load all transformed data into analytics schema

        Uses transactions for all-or-nothing semantics

        Args:
            transformed_data: Dictionary of transformed DataFrames

        Returns:
            Dictionary with load statistics
        """
        logger.info(f"Starting data load (ETL Run: {self.etl_run_id})")

        try:
            with self.olap_engine.begin() as conn:  # Transaction context
                logger.info("Transaction started")

                # Load in order (handle dependencies: dimensions first, then facts)
                self._load_dimension(
                    conn, "exchanges", transformed_data["dim_exchanges"]
                )
                self._load_dimension(
                    conn, "accounts", transformed_data["dim_accounts"]
                )
                self._load_dimension(
                    conn, "instruments", transformed_data["dim_instruments"]
                )
                self._load_dimension(
                    conn, "dates", transformed_data["dim_dates"]
                )

                # Delete existing facts (full refresh)
                self._delete_facts(conn)

                # Load facts
                self._load_fact(
                    conn, "fact_trades", transformed_data["fact_trades"]
                )
                self._load_fact(
                    conn, "fact_daily_holdings", 
                    transformed_data["fact_daily_holdings"]
                )
                self._load_fact(
                    conn, "fact_daily_account_summary",
                    transformed_data["fact_daily_account_summary"],
                )

                # Log audit trail
                self._log_audit_trail(conn)

                logger.info("Transaction committed successfully")

            logger.info("Data load completed successfully")
            return self.load_stats

        except Exception as e:
            logger.error(f"Data load failed: {str(e)}", exc_info=True)
            logger.error("Rolling back all changes due to error")
            raise

    def _load_dimension(
        self, conn, dim_name: str, df: pd.DataFrame
    ) -> int:
        """
        Load or update dimension table (Type 1: overwrite)

        Args:
            conn: Database connection
            dim_name: Dimension name (exchanges, accounts, etc.)
            df: DataFrame to load

        Returns:
            Number of rows loaded
        """
        logger.info(f"Loading dimension: {dim_name}")

        if df.empty:
            logger.warning(f"No data to load for dimension {dim_name}")
            return 0

        table_name = f"analytics.dim_{dim_name}"
        rows_loaded = 0

        try:
            # Map dimension names to primary key columns
            pk_map = {
                "exchanges": "exchange_id",
                "accounts": "account_id",
                "instruments": "instrument_id",
                "dates": "date_key",
            }

            pk_column = pk_map.get(dim_name)
            if not pk_column:
                raise ValueError(f"Unknown dimension: {dim_name}")

            # For Type 1 SCD: DELETE existing + INSERT new (current only)
            # Get unique keys
            pk_values = df[pk_column].unique()

            if len(pk_values) > 0:
                # Delete existing records for these keys
                placeholders = ",".join([f"'{v}'" for v in pk_values])
                delete_query = f"""
                DELETE FROM {table_name}
                WHERE {pk_column} IN ({placeholders})
                """
                conn.execute(text(delete_query))
                logger.debug(f"Deleted existing records for {dim_name}")

                # Load data in batches
                for i in range(0, len(df), BATCH_SIZE):
                    batch = df.iloc[i : i + BATCH_SIZE]
                    batch.to_sql(
                        f"dim_{dim_name}",
                        conn,
                        schema="analytics",
                        if_exists="append",
                        index=False,
                    )
                    rows_loaded += len(batch)
                    logger.debug(
                        f"Loaded batch {i//BATCH_SIZE + 1} "
                        f"({len(batch)} rows) for {dim_name}"
                    )

            conn.commit()  # Commit within transaction context
            logger.info(f"Loaded {rows_loaded} {dim_name} rows")
            self.load_stats[f"dim_{dim_name}"] = rows_loaded

            return rows_loaded

        except Exception as e:
            logger.error(f"Error loading dimension {dim_name}: {str(e)}", exc_info=True)
            raise

    def _delete_facts(self, conn) -> None:
        """Delete all existing fact tables (full refresh)"""
        logger.info("Deleting existing fact data (full refresh)")

        fact_tables = [
            "analytics.fact_trades",
            "analytics.fact_daily_holdings",
            "analytics.fact_daily_account_summary",
        ]

        for table in fact_tables:
            try:
                conn.execute(text(f"DELETE FROM {table}"))
                logger.debug(f"Deleted all rows from {table}")
            except exc.SQLAlchemyError as e:
                logger.error(f"Error deleting from {table}: {str(e)}", exc_info=True)
                raise

    def _load_fact(
        self, conn, fact_name: str, df: pd.DataFrame
    ) -> int:
        """
        Load fact table

        Args:
            conn: Database connection
            fact_name: Fact table name
            df: DataFrame to load

        Returns:
            Number of rows loaded
        """
        logger.info(f"Loading fact: {fact_name}")

        if df.empty:
            logger.warning(f"No data to load for fact {fact_name}")
            return 0

        rows_loaded = 0

        try:
            # Load data in batches
            for i in range(0, len(df), BATCH_SIZE):
                batch = df.iloc[i : i + BATCH_SIZE]
                batch.to_sql(
                    fact_name,
                    conn,
                    schema="analytics",
                    if_exists="append",
                    index=False,
                )
                rows_loaded += len(batch)
                logger.debug(
                    f"Loaded batch {i//BATCH_SIZE + 1} "
                    f"({len(batch)} rows) for {fact_name}"
                )

            logger.info(f"Loaded {rows_loaded} {fact_name} rows")
            self.load_stats[fact_name] = rows_loaded

            return rows_loaded

        except Exception as e:
            logger.error(f"Error loading fact {fact_name}: {str(e)}", exc_info=True)
            raise

    def _log_audit_trail(self, conn) -> None:
        """Log ETL operation to audit trail"""
        logger.info("Logging to audit trail")

        try:
            total_rows = sum(self.load_stats.values())

            audit_query = """
            INSERT INTO analytics.audit_trail
            (operation_timestamp, table_name, operation, rows_affected, status, etl_run_id)
            VALUES (CURRENT_TIMESTAMP, 'FACT_TABLES', 'LOAD', :rows, 'SUCCESS', :run_id)
            """

            conn.execute(
                text(audit_query),
                {"rows": total_rows, "run_id": self.etl_run_id},
            )

            logger.info(f"Logged {total_rows} rows to audit trail")

        except Exception as e:
            logger.error(f"Error logging to audit trail: {str(e)}", exc_info=True)
            raise

    def get_load_stats(self) -> Dict[str, int]:
        """Return load statistics"""
        return self.load_stats


def run_load(etl_run_id: str, transformed_data: Dict[str, pd.DataFrame]) -> Dict[str, int]:
    """
    Main entry point for loading

    Args:
        etl_run_id: Airflow DAG run ID
        transformed_data: Dictionary of transformed DataFrames

    Returns:
        Dictionary with load statistics
    """
    logger.info(f"=== DATA LOAD STARTED (Run: {etl_run_id}) ===")

    try:
        loader = DataLoader(etl_run_id)
        stats = loader.load_all_data(transformed_data)

        logger.info(f"=== DATA LOAD COMPLETED ===")
        logger.info(f"Load Summary: {stats}")

        return stats

    except Exception as e:
        logger.error(f"=== DATA LOAD FAILED ===", exc_info=True)
        raise


if __name__ == "__main__":
    # For local testing
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
    )

    logger.info("Load module can only be tested after extract and transform")
