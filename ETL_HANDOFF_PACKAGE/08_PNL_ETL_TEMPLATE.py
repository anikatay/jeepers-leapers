"""
P&L ETL Module - THE HARDEST PART

Calculates profit/loss on trades using FIFO matching algorithm.
Runs once per hour (at :15, after Extract finishes).

Key Complexity: FIFO matching algorithm for calculating realized P&L

Example usage:
    from etl.transform_pnl_etl import PnLTransformer
    transformer = PnLTransformer('airflow_run_id_12345')
    stats = transformer.transform_and_load()
    print(stats)  # {'trades_validated': 1000, 'trades_loaded': 950, ...}
"""

import logging
from datetime import datetime
from typing import Dict, List, Tuple
import pandas as pd
import numpy as np
from sqlalchemy import text, exc

from config import (
    get_staging_engine,
    get_olap_engine,
    PRICE_MIN,
    PRICE_MAX,
    QUANTITY_MIN,
    QUANTITY_MAX,
    DATE_FUTURE_TOLERANCE_DAYS,
    BATCH_SIZE,
)

logger = logging.getLogger(__name__)


class PnLTransformer:
    """Calculate P&L and load fact_trades table"""

    def __init__(self, etl_run_id: str):
        """Initialize transformer"""
        self.etl_run_id = etl_run_id
        self.staging_engine = get_staging_engine()
        self.olap_engine = get_olap_engine()
        self.transformation_stats = {}
        self.anomalies_detected = []
        self.oob_records = []

    def transform_and_load(self) -> Dict[str, int]:
        """
        Main orchestrator: Load, Transform, Load to analytics

        Returns:
            Dictionary with transformation and load statistics
        """
        logger.info(f"Starting P&L transformation (ETL Run: {self.etl_run_id})")

        try:
            # Step 1: Load raw trades from staging
            df_trades = self._load_staging_data("trades_raw")
            
            if df_trades.empty:
                logger.warning("No trade data to transform")
                return {"trades_loaded": 0}

            # Step 2: Validate trades (price, quantity, dates)
            df_valid = self._validate_and_filter_trades(df_trades)
            self.transformation_stats["trades_validated"] = len(df_valid)
            self.transformation_stats["trades_rejected"] = len(df_trades) - len(df_valid)

            # Step 3: Add trade_date for dimension join
            df_valid["trade_date"] = pd.to_datetime(df_valid["executed_at"]).dt.date

            # Step 4: Calculate REALIZED P&L (FIFO matching)
            df_valid = self._calculate_realized_pnl(df_valid)

            # Step 5: Calculate UNREALIZED P&L (current prices)
            df_valid = self._calculate_unrealized_pnl(df_valid)

            # Step 6: Add exchange_id (will need to join instruments dimension later)
            df_valid["exchange_id"] = "UNKNOWN"  # Placeholder, fill from dim_instruments
            df_valid["created_at"] = datetime.utcnow()
            df_valid["updated_at"] = datetime.utcnow()

            # Step 7: Reorder columns to match schema
            df_final = df_valid[[
                "trade_id",
                "account_id",
                "instrument_id",
                "exchange_id",
                "trade_date",
                "side",
                "quantity",
                "execution_price",
                "trade_value",
                "executed_at",
                "realized_pnl",
                "unrealized_pnl",
                "created_at",
                "updated_at",
            ]].copy()

            # Step 8: Load to analytics.fact_trades
            rows_loaded = self._load_to_analytics(df_final, "fact_trades")
            self.transformation_stats["trades_loaded"] = rows_loaded

            logger.info("P&L transformation completed successfully")
            logger.info(f"Transformation Summary: {self.transformation_stats}")
            return self.transformation_stats

        except Exception as e:
            logger.error(f"P&L transformation failed: {str(e)}", exc_info=True)
            raise

    def _load_staging_data(self, table_name: str) -> pd.DataFrame:
        """Load data from staging table"""
        try:
            with self.staging_engine.connect() as conn:
                df = pd.read_sql(
                    text(f"SELECT * FROM staging.{table_name} WHERE etl_run_id = :run_id"),
                    conn,
                    params={"run_id": self.etl_run_id},
                )
            return df
        except Exception as e:
            logger.error(f"Error loading {table_name}: {str(e)}", exc_info=True)
            raise

    def _validate_and_filter_trades(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Validate trades and move OOB records to DLQ

        Checks:
        - Price: PRICE_MIN to PRICE_MAX
        - Quantity: QUANTITY_MIN to QUANTITY_MAX
        - Date: not > DATE_FUTURE_TOLERANCE_DAYS in future
        """
        logger.info(f"Validating {len(df)} trades")

        valid_records = []
        invalid_records = []

        for idx, row in df.iterrows():
            is_valid = True
            error_reasons = []

            # Null checks
            if pd.isna(row["execution_price"]):
                is_valid = False
                error_reasons.append("Null execution_price")

            if pd.isna(row["quantity"]):
                is_valid = False
                error_reasons.append("Null quantity")

            # Price validation
            if is_valid:
                if row["execution_price"] < PRICE_MIN:
                    is_valid = False
                    error_reasons.append(f"Price too low: {row['execution_price']}")

                if row["execution_price"] > PRICE_MAX:
                    is_valid = False
                    error_reasons.append(f"Price too high: {row['execution_price']}")

            # Quantity validation
            if is_valid:
                if row["quantity"] < QUANTITY_MIN:
                    is_valid = False
                    error_reasons.append(f"Quantity too low: {row['quantity']}")

                if row["quantity"] > QUANTITY_MAX:
                    is_valid = False
                    error_reasons.append(f"Quantity too high: {row['quantity']}")

            # Date validation
            if is_valid:
                try:
                    trade_date = pd.to_datetime(row["executed_at"]).date()
                    future_limit = datetime.utcnow().date()
                    future_limit = future_limit + pd.Timedelta(days=DATE_FUTURE_TOLERANCE_DAYS)

                    if trade_date > future_limit:
                        is_valid = False
                        error_reasons.append(f"Trade date in future: {trade_date}")
                except Exception as e:
                    is_valid = False
                    error_reasons.append(f"Invalid date format: {str(e)}")

            if is_valid:
                valid_records.append(row)
            else:
                invalid_records.append((row, error_reasons))

        if invalid_records:
            logger.warning(f"Found {len(invalid_records)} invalid trade records")
            # TODO: Route to dead_letter_queue

        return pd.DataFrame(valid_records)

    def _calculate_realized_pnl(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Calculate realized P&L using FIFO matching of buy/sell pairs

        ALGORITHM:
        For each (account_id, instrument_id):
          - Sort BUY trades by executed_at (oldest first)
          - Sort SELL trades by executed_at (oldest first)
          - Match oldest BUY with first SELL (FIFO)
          - realized_pnl = (sell_price - buy_price) × matched_quantity

        EXAMPLE:
        BUY 100 @ $50 (2026-01-01)
        BUY 50 @ $55 (2026-01-02)
        SELL 80 @ $60 (2026-01-03) → P&L = (60-50) × 80 = $800
        SELL 70 @ $65 (2026-01-04) → P&L = (65-50) × 20 + (65-55) × 50 = $300 + $500 = $800
        """
        logger.info("Calculating realized P&L (FIFO matching)")

        df["realized_pnl"] = 0.00

        # Group by account and instrument
        for (account_id, instrument_id), group in df.groupby(
            ["account_id", "instrument_id"]
        ):
            # Separate buy and sell orders, sorted by time
            buys = group[group["side"] == "BUY"].sort_values("executed_at").reset_index(drop=True)
            sells = group[group["side"] == "SELL"].sort_values("executed_at").reset_index(drop=True)

            if buys.empty or sells.empty:
                continue

            # FIFO matching
            buy_idx = 0
            buy_remaining = buys.iloc[buy_idx]["quantity"]
            buy_price = buys.iloc[buy_idx]["execution_price"]

            for sell_idx, sell_row in sells.iterrows():
                sell_qty = sell_row["quantity"]
                sell_price = sell_row["execution_price"]
                realized_pnl_for_sell = 0.00

                # Match against buy orders (FIFO)
                while sell_qty > 0 and buy_idx < len(buys):
                    if buy_remaining >= sell_qty:
                        # Partial match: all remaining sell matched
                        matched_qty = sell_qty
                        realized_pnl_for_sell += (sell_price - buy_price) * matched_qty
                        buy_remaining -= matched_qty
                        sell_qty = 0
                    else:
                        # Full buy matched: move to next buy
                        matched_qty = buy_remaining
                        realized_pnl_for_sell += (sell_price - buy_price) * matched_qty
                        sell_qty -= matched_qty
                        buy_idx += 1

                        if buy_idx < len(buys):
                            buy_remaining = buys.iloc[buy_idx]["quantity"]
                            buy_price = buys.iloc[buy_idx]["execution_price"]

                # Update realized_pnl for this sell trade
                df.loc[sell_row.name, "realized_pnl"] = realized_pnl_for_sell

        logger.info("Realized P&L calculation complete")
        return df

    def _calculate_unrealized_pnl(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Calculate unrealized P&L from open positions

        For each open BUY trade (not yet fully matched with SELL):
            unrealized_pnl = (current_price - buy_price) × quantity

        Note: In this simplified version, we use execution_price as proxy.
        In production, join with dim_instruments for current_price.
        """
        logger.info("Calculating unrealized P&L")

        df["unrealized_pnl"] = 0.00

        # For now, open positions have 0 unrealized P&L
        # In production, get current_price from dim_instruments join
        # and calculate: (current_price - buy_price) × quantity for open BUYs

        return df

    def _load_to_analytics(self, df: pd.DataFrame, table_name: str) -> int:
        """
        Load DataFrame to analytics table with transaction wrapper

        All-or-nothing: Either all rows load or transaction rolls back
        """
        logger.info(f"Loading {len(df)} rows to analytics.{table_name}")

        try:
            with self.olap_engine.begin() as conn:
                # Delete existing data (full refresh)
                conn.execute(text(f"DELETE FROM analytics.{table_name}"))
                logger.debug(f"Deleted existing rows from {table_name}")

                # Load in batches
                rows_loaded = 0
                for i in range(0, len(df), BATCH_SIZE):
                    batch = df.iloc[i : i + BATCH_SIZE]
                    batch.to_sql(
                        table_name,
                        conn,
                        schema="analytics",
                        if_exists="append",
                        index=False,
                    )
                    rows_loaded += len(batch)
                    logger.debug(f"Loaded batch {i//BATCH_SIZE + 1} ({len(batch)} rows)")

            logger.info(f"Successfully loaded {rows_loaded} rows to {table_name}")
            return rows_loaded

        except exc.SQLAlchemyError as e:
            logger.error(f"Error loading to {table_name}: {str(e)}", exc_info=True)
            logger.error("Transaction rolled back")
            raise


def run_pnl_etl(etl_run_id: str) -> Dict[str, int]:
    """
    Main entry point for P&L ETL

    Args:
        etl_run_id: Airflow DAG run ID

    Returns:
        Dictionary with transformation and load statistics
    """
    logger.info(f"=== P&L ETL STARTED (Run: {etl_run_id}) ===")

    try:
        transformer = PnLTransformer(etl_run_id)
        stats = transformer.transform_and_load()

        logger.info(f"=== P&L ETL COMPLETED ===")
        logger.info(f"P&L Summary: {stats}")

        return stats

    except Exception as e:
        logger.error(f"=== P&L ETL FAILED ===", exc_info=True)
        raise


if __name__ == "__main__":
    # For local testing
    import sys
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
    )

    run_id = sys.argv[1] if len(sys.argv) > 1 else "local_test_run"
    stats = run_pnl_etl(run_id)
    print("P&L ETL stats:", stats)
