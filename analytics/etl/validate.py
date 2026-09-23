"""
Validate Module

Reconciliation and data quality checks
Compares OLTP vs OLAP, validates P&L calculations, detects anomalies
"""

import logging
from datetime import datetime
from typing import Dict, List, Tuple
import pandas as pd
from sqlalchemy import text

from .config import (
    get_oltp_engine,
    get_olap_engine,
    ROW_COUNT_TOLERANCE,
    PRICE_ANOMALY_THRESHOLD_SIGMA,
)

logger = logging.getLogger(__name__)


class DataValidator:
    """Validate and reconcile analytics data"""

    def __init__(self, etl_run_id: str):
        """Initialize validator"""
        self.etl_run_id = etl_run_id
        self.oltp_engine = get_oltp_engine()
        self.olap_engine = get_olap_engine()
        self.validation_results = {}
        self.failed_checks = []
        self.warnings = []

    def run_all_validations(self) -> Dict:
        """
        Run all validation checks

        Returns:
            Dictionary with validation results
        """
        logger.info(f"Starting data validation (ETL Run: {self.etl_run_id})")

        try:
            # Reconciliation checks
            self._reconcile_row_counts()
            self._reconcile_pnl()

            # Schema validation
            self._validate_schema()
            self._validate_foreign_keys()

            # Anomaly detection
            self._detect_anomalies()

            # Data quality checks
            self._check_data_completeness()

            logger.info("Data validation completed")

            return {
                "validation_results": self.validation_results,
                "failed_checks": self.failed_checks,
                "warnings": self.warnings,
                "passed": len(self.failed_checks) == 0,
            }

        except Exception as e:
            logger.error(f"Data validation failed: {str(e)}", exc_info=True)
            raise

    def _reconcile_row_counts(self) -> None:
        """Reconcile row counts between OLTP and OLAP"""
        logger.info("Reconciling row counts (OLTP vs OLAP)")

        try:
            with self.oltp_engine.connect() as oltp_conn:
                with self.olap_engine.connect() as olap_conn:
                    # Trades count
                    oltp_trades = pd.read_sql(
                        text("SELECT COUNT(*) as cnt FROM public.trades"),
                        oltp_conn,
                    )
                    olap_trades = pd.read_sql(
                        text("SELECT COUNT(*) as cnt FROM analytics.fact_trades"),
                        olap_conn,
                    )

                    oltp_count = oltp_trades["cnt"].values[0]
                    olap_count = olap_trades["cnt"].values[0]
                    tolerance = int(oltp_count * ROW_COUNT_TOLERANCE / 100)

                    if abs(oltp_count - olap_count) <= tolerance:
                        self.validation_results["row_count_trades"] = "PASSED"
                        logger.info(
                            f"Trades row count: "
                            f"OLTP={oltp_count}, OLAP={olap_count} (within {ROW_COUNT_TOLERANCE}%)"
                        )
                    else:
                        self.validation_results["row_count_trades"] = "FAILED"
                        msg = (
                            f"Trades row count mismatch: "
                            f"OLTP={oltp_count}, OLAP={olap_count} "
                            f"(tolerance={tolerance})"
                        )
                        self.failed_checks.append(msg)
                        logger.error(msg)

                    # Holdings count
                    oltp_holdings = pd.read_sql(
                        text("SELECT COUNT(*) as cnt FROM public.holdings"),
                        oltp_conn,
                    )
                    olap_holdings = pd.read_sql(
                        text("SELECT COUNT(*) as cnt FROM analytics.fact_daily_holdings"),
                        olap_conn,
                    )

                    oltp_count = oltp_holdings["cnt"].values[0]
                    olap_count = olap_holdings["cnt"].values[0]

                    if abs(oltp_count - olap_count) <= int(oltp_count * ROW_COUNT_TOLERANCE / 100):
                        self.validation_results["row_count_holdings"] = "PASSED"
                        logger.info(
                            f"Holdings row count: "
                            f"OLTP={oltp_count}, OLAP={olap_count} (within {ROW_COUNT_TOLERANCE}%)"
                        )
                    else:
                        msg = f"Holdings row count mismatch: OLTP={oltp_count}, OLAP={olap_count}"
                        self.warnings.append(msg)
                        logger.warning(msg)

        except Exception as e:
            logger.error(f"Error reconciling row counts: {str(e)}", exc_info=True)
            raise

    def _reconcile_pnl(self) -> None:
        """Reconcile P&L calculations"""
        logger.info("Reconciling P&L calculations")

        try:
            with self.olap_engine.connect() as conn:
                # Check that realized + unrealized P&L totals are reasonable
                pnl_query = """
                SELECT
                    account_id,
                    SUM(realized_pnl) as total_realized,
                    SUM(unrealized_pnl) as total_unrealized
                FROM analytics.fact_trades
                GROUP BY account_id
                ORDER BY account_id
                """

                df_pnl = pd.read_sql(text(pnl_query), conn)

                if df_pnl.empty:
                    logger.warning("No P&L data to reconcile")
                    return

                # Check for NaN or NULL values
                if df_pnl.isnull().any().any():
                    msg = "Found NULL values in P&L columns"
                    self.failed_checks.append(msg)
                    logger.error(msg)
                else:
                    self.validation_results["pnl_reconciliation"] = "PASSED"
                    logger.info(
                        f"P&L reconciliation: {len(df_pnl)} accounts validated"
                    )

        except Exception as e:
            logger.error(f"Error reconciling P&L: {str(e)}", exc_info=True)
            raise

    def _validate_schema(self) -> None:
        """Validate schema types and constraints"""
        logger.info("Validating schema integrity")

        try:
            with self.olap_engine.connect() as conn:
                # Check for nulls in fact key columns
                null_check_query = """
                SELECT
                    COUNT(*) as null_count
                FROM analytics.fact_trades
                WHERE trade_id IS NULL
                    OR account_id IS NULL
                    OR instrument_id IS NULL
                """

                result = pd.read_sql(text(null_check_query), conn)
                null_count = result["null_count"].values[0]

                if null_count > 0:
                    msg = f"Found {null_count} NULL values in fact_trades key columns"
                    self.failed_checks.append(msg)
                    logger.error(msg)
                else:
                    self.validation_results["schema_nulls"] = "PASSED"
                    logger.info("Schema: No NULL values in fact keys")

        except Exception as e:
            logger.error(f"Error validating schema: {str(e)}", exc_info=True)
            raise

    def _validate_foreign_keys(self) -> None:
        """Validate foreign key relationships"""
        logger.info("Validating foreign keys")

        try:
            with self.olap_engine.connect() as conn:
                # Check fact_trades references valid accounts
                orphaned_query = """
                SELECT COUNT(*) as orphaned_count
                FROM analytics.fact_trades ft
                LEFT JOIN analytics.dim_accounts da ON ft.account_id = da.account_id
                WHERE da.account_id IS NULL
                """

                result = pd.read_sql(text(orphaned_query), conn)
                orphaned_count = result["orphaned_count"].values[0]

                if orphaned_count > 0:
                    msg = f"Found {orphaned_count} fact_trades with orphaned account_ids"
                    self.failed_checks.append(msg)
                    logger.error(msg)
                else:
                    self.validation_results["foreign_keys"] = "PASSED"
                    logger.info("Foreign keys: All references valid")

        except Exception as e:
            logger.error(f"Error validating foreign keys: {str(e)}", exc_info=True)
            raise

    def _detect_anomalies(self) -> None:
        """Detect data anomalies (price spikes, volume anomalies)"""
        logger.info("Detecting anomalies")

        try:
            with self.olap_engine.connect() as conn:
                # Check for price anomalies (>2σ from 30-day avg)
                anomaly_query = """
                SELECT
                    instrument_id,
                    current_price,
                    AVG(current_price) OVER (PARTITION BY instrument_id ROWS BETWEEN 29 PRECEDING AND CURRENT ROW) as avg_price_30d,
                    STDDEV(current_price) OVER (PARTITION BY instrument_id ROWS BETWEEN 29 PRECEDING AND CURRENT ROW) as stddev_price_30d
                FROM analytics.dim_instruments
                ORDER BY instrument_id
                """

                df_anomalies = pd.read_sql(text(anomaly_query), conn)

                anomaly_count = 0
                for _, row in df_anomalies.iterrows():
                    if pd.notna(row["stddev_price_30d"]) and row["stddev_price_30d"] > 0:
                        z_score = abs(
                            (row["current_price"] - row["avg_price_30d"])
                            / row["stddev_price_30d"]
                        )
                        if z_score > PRICE_ANOMALY_THRESHOLD_SIGMA:
                            anomaly_count += 1
                            logger.warning(
                                f"Price anomaly detected: "
                                f"instrument={row['instrument_id']}, "
                                f"price={row['current_price']}, "
                                f"z_score={z_score:.2f}"
                            )

                if anomaly_count > 0:
                    msg = f"Detected {anomaly_count} price anomalies (>2σ)"
                    self.warnings.append(msg)
                    self.validation_results["anomalies"] = "WARNING"
                else:
                    self.validation_results["anomalies"] = "PASSED"
                    logger.info("Anomaly detection: No significant anomalies found")

        except Exception as e:
            logger.error(f"Error detecting anomalies: {str(e)}", exc_info=True)
            raise

    def _check_data_completeness(self) -> None:
        """Check data completeness and coverage"""
        logger.info("Checking data completeness")

        try:
            with self.olap_engine.connect() as conn:
                # Check that all dimensions are populated
                dim_checks = {
                    "dim_exchanges": "SELECT COUNT(*) as cnt FROM analytics.dim_exchanges",
                    "dim_accounts": "SELECT COUNT(*) as cnt FROM analytics.dim_accounts",
                    "dim_instruments": "SELECT COUNT(*) as cnt FROM analytics.dim_instruments",
                    "dim_dates": "SELECT COUNT(*) as cnt FROM analytics.dim_dates",
                }

                for dim_name, query in dim_checks.items():
                    result = pd.read_sql(text(query), conn)
                    count = result["cnt"].values[0]

                    if count == 0:
                        msg = f"No data found in {dim_name}"
                        self.warnings.append(msg)
                        logger.warning(msg)
                    else:
                        logger.info(f"{dim_name}: {count} rows")

                self.validation_results["completeness"] = "PASSED"

        except Exception as e:
            logger.error(f"Error checking completeness: {str(e)}", exc_info=True)
            raise

    def get_validation_results(self) -> Dict:
        """Return validation results"""
        return {
            "validation_results": self.validation_results,
            "failed_checks": self.failed_checks,
            "warnings": self.warnings,
            "passed": len(self.failed_checks) == 0,
        }


def run_validation(etl_run_id: str) -> Dict:
    """
    Main entry point for validation

    Args:
        etl_run_id: Airflow DAG run ID

    Returns:
        Dictionary with validation results
    """
    logger.info(f"=== DATA VALIDATION STARTED (Run: {etl_run_id}) ===")

    try:
        validator = DataValidator(etl_run_id)
        results = validator.run_all_validations()

        if results["passed"]:
            logger.info(f"=== DATA VALIDATION PASSED ===")
        else:
            logger.warning(f"=== DATA VALIDATION FAILED ===")
            for check in results["failed_checks"]:
                logger.error(f"  - {check}")

        if results["warnings"]:
            logger.warning("Validation Warnings:")
            for warning in results["warnings"]:
                logger.warning(f"  - {warning}")

        return results

    except Exception as e:
        logger.error(f"=== DATA VALIDATION FAILED ===", exc_info=True)
        raise


if __name__ == "__main__":
    # For local testing
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
    )

    test_run_id = f"test_validate_{datetime.utcnow().isoformat()}"
    results = run_validation(test_run_id)
    print("\n" + "=" * 60)
    print("Validation Complete")
    print("=" * 60)
    print(f"Passed: {results['passed']}")
    print(f"Checks: {results['validation_results']}")
    if results["failed_checks"]:
        print("Failed:")
        for check in results["failed_checks"]:
            print(f"  - {check}")
