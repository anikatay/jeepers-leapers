"""
Transform Module

Transforms extracted staging data into analytics schema format
Includes P&L calculations (hybrid: realized + unrealized), aggregations, and quality checks
"""

import logging
from datetime import datetime, date
from typing import Dict, Tuple, List
import pandas as pd
import numpy as np
from sqlalchemy import text

from .config import (
    get_staging_engine,
    PRICE_MIN,
    PRICE_MAX,
    QUANTITY_MIN,
    QUANTITY_MAX,
    DATE_FUTURE_TOLERANCE_DAYS,
    PRICE_ANOMALY_THRESHOLD_SIGMA,
)

logger = logging.getLogger(__name__)


class DataTransformer:
    """Transform staging data into analytics-ready format"""

    def __init__(self, etl_run_id: str):
        """Initialize transformer"""
        self.etl_run_id = etl_run_id
        self.staging_engine = get_staging_engine()
        self.transformation_stats = {}
        self.anomalies_detected = []
        self.oob_records = []

    def transform_all_data(self) -> Dict[str, int]:
        """
        Transform all staging data

        Returns:
            Dictionary with counts of transformed data
        """
        logger.info(f"Starting data transformation (ETL Run: {self.etl_run_id})")

        try:
            # Load dimensions
            self.dim_accounts = self._transform_accounts()
            self.dim_instruments = self._transform_instruments()
            self.dim_exchanges = self._transform_exchanges()
            self.dim_dates = self._transform_dates()

            # Transform facts
            self.fact_trades = self._transform_trades()
            self.fact_daily_holdings = self._transform_daily_holdings()
            self.fact_daily_account_summary = self._transform_daily_account_summary()

            # Validate all transformed data
            self._validate_transformed_data()

            logger.info("Data transformation completed successfully")
            return {
                "dim_accounts": len(self.dim_accounts),
                "dim_instruments": len(self.dim_instruments),
                "dim_exchanges": len(self.dim_exchanges),
                "dim_dates": len(self.dim_dates),
                "fact_trades": len(self.fact_trades),
                "fact_daily_holdings": len(self.fact_daily_holdings),
                "fact_daily_account_summary": len(self.fact_daily_account_summary),
                "anomalies_detected": len(self.anomalies_detected),
                "oob_records": len(self.oob_records),
            }

        except Exception as e:
            logger.error(f"Data transformation failed: {str(e)}", exc_info=True)
            raise

    def _load_staging_data(self, table_name: str) -> pd.DataFrame:
        """Load data from staging table into DataFrame"""
        with self.staging_engine.connect() as conn:
            df = pd.read_sql(
                text(f"SELECT * FROM staging.{table_name} WHERE etl_run_id = :run_id"),
                conn,
                params={"run_id": self.etl_run_id},
            )
        return df

    def _transform_exchanges(self) -> pd.DataFrame:
        """Transform exchanges dimension"""
        logger.info("Transforming exchanges")

        df = self._load_staging_data("exchanges_raw")

        if df.empty:
            logger.warning("No exchange data to transform")
            return pd.DataFrame()

        # Minimal transformation (Type 1 dimension - current only)
        df_dim = df[
            [
                "exchange_id",
                "exchange_name",
                "region",
                "timezone",
                "currency",
                "created_at",
            ]
        ].copy()

        df_dim["updated_at"] = datetime.utcnow()

        logger.info(f"Transformed {len(df_dim)} exchanges")
        return df_dim

    def _transform_accounts(self) -> pd.DataFrame:
        """Transform accounts dimension"""
        logger.info("Transforming accounts")

        df = self._load_staging_data("accounts_raw")

        if df.empty:
            logger.warning("No account data to transform")
            return pd.DataFrame()

        df_dim = df[
            ["account_id", "user_id", "status", "currency", "created_at"]
        ].copy()

        df_dim.rename(
            columns={"status": "account_status"}, inplace=True
        )  # Map to schema
        df_dim["updated_at"] = datetime.utcnow()

        logger.info(f"Transformed {len(df_dim)} accounts")
        return df_dim

    def _transform_instruments(self) -> pd.DataFrame:
        """Transform instruments dimension"""
        logger.info("Transforming instruments")

        df = self._load_staging_data("instruments_raw")

        if df.empty:
            logger.warning("No instrument data to transform")
            return pd.DataFrame()

        df_dim = df[
            [
                "instrument_id",
                "ticker",
                "instrument_name",
                "exchange_id",
                "current_price",
                "price_updated_at",
                "created_at",
                "updated_at",
            ]
        ].copy()

        # Validate prices
        df_dim.loc[
            (df_dim["current_price"] < PRICE_MIN) | (df_dim["current_price"] > PRICE_MAX),
            "current_price",
        ] = 0.00

        df_dim["sector"] = "Unknown"  # Extensible for future
        df_dim["instrument_type"] = "STOCK"  # Extensible for future
        df_dim["updated_at"] = datetime.utcnow()

        logger.info(f"Transformed {len(df_dim)} instruments")
        return df_dim

    def _transform_dates(self) -> pd.DataFrame:
        """Transform dates dimension (generate date range)"""
        logger.info("Transforming date dimension")

        # Generate date range from 2 years ago to 1 year in future
        start_date = date(2023, 1, 1)
        end_date = date(2026, 12, 31)

        date_range = pd.date_range(start=start_date, end=end_date, freq="D")

        df_dim = pd.DataFrame({"date_full": date_range.date})

        df_dim["date_key"] = df_dim["date_full"]
        df_dim["year"] = df_dim["date_full"].dt.year
        df_dim["quarter"] = df_dim["date_full"].dt.quarter
        df_dim["month"] = df_dim["date_full"].dt.month
        df_dim["month_name"] = df_dim["date_full"].dt.strftime("%B")
        df_dim["day_of_month"] = df_dim["date_full"].dt.day
        df_dim["day_of_week"] = df_dim["date_full"].dt.dayofweek  # 0=Monday, 6=Sunday
        df_dim["day_name"] = df_dim["date_full"].dt.strftime("%A")
        df_dim["week_of_year"] = df_dim["date_full"].dt.isocalendar().week
        df_dim["is_weekday"] = df_dim["day_of_week"] < 5  # Mon-Fri
        df_dim["is_trading_day"] = True  # Simplified (can add holiday logic)
        df_dim["created_at"] = datetime.utcnow()

        # Reorder columns to match schema
        df_dim = df_dim[
            [
                "date_key",
                "date_full",
                "year",
                "quarter",
                "month",
                "month_name",
                "day_of_month",
                "day_of_week",
                "day_name",
                "week_of_year",
                "is_weekday",
                "is_trading_day",
                "created_at",
            ]
        ]

        logger.info(f"Transformed {len(df_dim)} dates")
        return df_dim

    def _transform_trades(self) -> pd.DataFrame:
        """Transform trades fact (with hybrid P&L: realized + unrealized)"""
        logger.info("Transforming trades")

        df = self._load_staging_data("trades_raw")

        if df.empty:
            logger.warning("No trade data to transform")
            return pd.DataFrame()

        df_fact = df.copy()

        # Validate and filter out-of-bounds records
        df_fact = self._validate_and_filter_trades(df_fact)

        # Add trade_date for joining with date dimension
        df_fact["trade_date"] = pd.to_datetime(df_fact["executed_at"]).dt.date

        # Calculate REALIZED P&L (matched buy/sell pairs using FIFO)
        df_fact = self._calculate_realized_pnl(df_fact)

        # Calculate UNREALIZED P&L (using current prices from dim_instruments)
        df_fact = self._calculate_unrealized_pnl(df_fact)

        # Reorder columns to match schema
        df_fact = df_fact[
            [
                "trade_id",
                "account_id",
                "instrument_id",
                "side",
                "trade_date",
                "quantity",
                "execution_price",
                "trade_value",
                "executed_at",
                "realized_pnl",
                "unrealized_pnl",
            ]
        ].copy()

        # Add exchange_id from instruments dimension
        if not self.dim_instruments.empty:
            exchange_map = self.dim_instruments[
                ["instrument_id", "exchange_id"]
            ].drop_duplicates()
            df_fact = df_fact.merge(
                exchange_map, on="instrument_id", how="left"
            )

        df_fact["created_at"] = datetime.utcnow()
        df_fact["updated_at"] = datetime.utcnow()

        logger.info(
            f"Transformed {len(df_fact)} trades (anomalies: {len(self.anomalies_detected)})"
        )
        return df_fact

    def _validate_and_filter_trades(self, df: pd.DataFrame) -> pd.DataFrame:
        """Validate trades and move OOB records to DLQ"""
        logger.info("Validating trades")

        valid_records = []
        invalid_records = []

        for idx, row in df.iterrows():
            is_valid = True
            error_reasons = []

            # Price validation
            if pd.isna(row["execution_price"]) or row["execution_price"] <= PRICE_MIN:
                is_valid = False
                error_reasons.append(f"Price too low: {row['execution_price']}")

            if row["execution_price"] > PRICE_MAX:
                is_valid = False
                error_reasons.append(f"Price too high: {row['execution_price']}")

            # Quantity validation
            if pd.isna(row["quantity"]) or row["quantity"] < QUANTITY_MIN:
                is_valid = False
                error_reasons.append(f"Quantity too low: {row['quantity']}")

            if row["quantity"] > QUANTITY_MAX:
                is_valid = False
                error_reasons.append(f"Quantity too high: {row['quantity']}")

            # Date validation
            trade_date = pd.to_datetime(row["executed_at"]).date()
            future_limit = (
                datetime.utcnow().date() + 
                pd.Timedelta(days=DATE_FUTURE_TOLERANCE_DAYS)
            )
            if trade_date > future_limit:
                is_valid = False
                error_reasons.append(
                    f"Trade date in future: {trade_date}"
                )

            if is_valid:
                valid_records.append(row)
            else:
                invalid_records.append((row, error_reasons))
                self.oob_records.append(row)

        if invalid_records:
            logger.warning(f"Found {len(invalid_records)} OOB trade records")
            for row, reasons in invalid_records:
                logger.debug(f"OOB Record: {row['trade_id']} - {reasons}")

        return pd.DataFrame(valid_records)

    def _calculate_realized_pnl(self, df: pd.DataFrame) -> pd.DataFrame:
        """Calculate realized P&L using FIFO matching of buy/sell pairs"""
        logger.info("Calculating realized P&L (FIFO matching)")

        df["realized_pnl"] = 0.00

        # Group by account and instrument
        for (account_id, instrument_id), group in df.groupby(
            ["account_id", "instrument_id"]
        ):
            # Separate buy and sell orders
            buys = group[group["side"] == "BUY"].sort_values("executed_at")
            sells = group[group["side"] == "SELL"].sort_values("executed_at")

            if buys.empty or sells.empty:
                continue

            # FIFO matching: match oldest buys with sells
            buy_idx = 0
            buy_remaining = buys.iloc[buy_idx]["quantity"]
            buy_price = buys.iloc[buy_idx]["execution_price"]

            for sell_idx, sell_row in sells.iterrows():
                sell_qty = sell_row["quantity"]
                sell_price = sell_row["execution_price"]

                while sell_qty > 0 and buy_idx < len(buys):
                    if buy_remaining >= sell_qty:
                        # Partial match
                        matched_qty = sell_qty
                        realized_pnl = (sell_price - buy_price) * matched_qty
                        df.loc[sell_idx, "realized_pnl"] = realized_pnl
                        buy_remaining -= matched_qty
                        sell_qty = 0
                    else:
                        # Full buy matched
                        matched_qty = buy_remaining
                        realized_pnl = (sell_price - buy_price) * matched_qty
                        df.loc[sell_idx, "realized_pnl"] += realized_pnl
                        sell_qty -= matched_qty
                        buy_idx += 1

                        if buy_idx < len(buys):
                            buy_remaining = buys.iloc[buy_idx]["quantity"]
                            buy_price = buys.iloc[buy_idx]["execution_price"]

        return df

    def _calculate_unrealized_pnl(self, df: pd.DataFrame) -> pd.DataFrame:
        """Calculate unrealized P&L from open positions"""
        logger.info("Calculating unrealized P&L (daily snapshots)")

        df["unrealized_pnl"] = 0.00

        # Get current prices from instruments dimension
        if self.dim_instruments.empty:
            logger.warning("No instruments data for unrealized P&L calculation")
            return df

        instrument_prices = self.dim_instruments[
            ["instrument_id", "current_price"]
        ].drop_duplicates()

        for idx, row in df.iterrows():
            instrument_data = instrument_prices[
                instrument_prices["instrument_id"] == row["instrument_id"]
            ]

            if not instrument_data.empty:
                current_price = instrument_data.iloc[0]["current_price"]

                # For open positions (BUY orders not yet sold)
                if row["side"] == "BUY":
                    cost_basis = row["execution_price"]
                    unrealized_pnl = (
                        (current_price - cost_basis) * row["quantity"]
                    )
                    df.loc[idx, "unrealized_pnl"] = unrealized_pnl

        return df

    def _transform_daily_holdings(self) -> pd.DataFrame:
        """Transform daily holdings snapshots"""
        logger.info("Transforming daily holdings")

        holdings = self._load_staging_data("holdings_raw")

        if holdings.empty:
            logger.warning("No holdings data to transform")
            return pd.DataFrame()

        # Get current trades with prices
        trades = self.fact_trades.copy() if not self.fact_trades.empty else pd.DataFrame()

        if trades.empty:
            logger.warning("No trades data for holdings transformation")
            return pd.DataFrame()

        # Calculate cost basis and market value per position
        holdings_list = []

        for _, holding_row in holdings.iterrows():
            account_id = holding_row["account_id"]
            instrument_id = holding_row["instrument_id"]
            quantity = holding_row["quantity"]

            # Find trades for this position
            position_trades = trades[
                (trades["account_id"] == account_id)
                & (trades["instrument_id"] == instrument_id)
            ]

            if not position_trades.empty:
                # Calculate cost basis (FIFO average)
                total_cost = 0
                total_quantity = 0

                for _, trade in position_trades.iterrows():
                    if trade["side"] == "BUY":
                        total_cost += trade["execution_price"] * trade["quantity"]
                        total_quantity += trade["quantity"]
                    else:
                        total_quantity -= trade["quantity"]

                cost_basis = (
                    total_cost / quantity if quantity > 0 else 0
                )

                # Get current price
                instrument_price = self.dim_instruments[
                    self.dim_instruments["instrument_id"] == instrument_id
                ]
                current_price = (
                    instrument_price.iloc[0]["current_price"]
                    if not instrument_price.empty
                    else 0
                )

                market_value = quantity * current_price
                unrealized_pnl = market_value - (quantity * cost_basis)

                holdings_list.append(
                    {
                        "account_id": account_id,
                        "instrument_id": instrument_id,
                        "snapshot_date": datetime.utcnow().date(),
                        "quantity": quantity,
                        "cost_basis": cost_basis,
                        "market_value": market_value,
                        "unrealized_pnl": unrealized_pnl,
                        "created_at": datetime.utcnow(),
                        "updated_at": datetime.utcnow(),
                    }
                )

        df_holdings = pd.DataFrame(holdings_list)
        logger.info(f"Transformed {len(df_holdings)} daily holdings")
        return df_holdings

    def _transform_daily_account_summary(self) -> pd.DataFrame:
        """Transform daily account summaries (aggregates)"""
        logger.info("Transforming daily account summaries")

        if self.fact_trades.empty:
            logger.warning("No trades data for account summary")
            return pd.DataFrame()

        summary_list = []

        # Aggregate by account
        for account_id in self.fact_trades["account_id"].unique():
            account_trades = self.fact_trades[
                self.fact_trades["account_id"] == account_id
            ]

            # Get account data
            account_data = self.dim_accounts[
                self.dim_accounts["account_id"] == account_id
            ]
            if account_data.empty:
                continue

            beginning_balance = 0  # TODO: Get from OLTP accounts table
            daily_pnl = (
                account_trades["realized_pnl"].sum()
                + account_trades["unrealized_pnl"].sum()
            )
            ending_balance = beginning_balance + daily_pnl

            num_buy_trades = len(account_trades[account_trades["side"] == "BUY"])
            num_sell_trades = len(
                account_trades[account_trades["side"] == "SELL"]
            )
            total_shares = account_trades["quantity"].sum()
            total_volume = account_trades["trade_value"].sum()

            # Get number of positions from holdings
            holdings = self.fact_daily_holdings[
                self.fact_daily_holdings["account_id"] == account_id
            ]
            num_positions = len(holdings)
            largest_position = (
                holdings["market_value"].max() if not holdings.empty else 0
            )

            summary_list.append(
                {
                    "account_id": account_id,
                    "summary_date": datetime.utcnow().date(),
                    "beginning_balance": beginning_balance,
                    "ending_balance": ending_balance,
                    "daily_pnl": daily_pnl,
                    "cumulative_pnl": daily_pnl,  # TODO: Calculate cumulative
                    "num_buy_trades": num_buy_trades,
                    "num_sell_trades": num_sell_trades,
                    "total_shares_traded": total_shares,
                    "total_volume_traded": total_volume,
                    "num_positions": num_positions,
                    "largest_position_value": largest_position,
                    "created_at": datetime.utcnow(),
                    "updated_at": datetime.utcnow(),
                }
            )

        df_summary = pd.DataFrame(summary_list)
        logger.info(f"Transformed {len(df_summary)} account summaries")
        return df_summary

    def _validate_transformed_data(self) -> None:
        """Validate all transformed data"""
        logger.info("Validating transformed data")

        # Check for nulls in fact keys
        if not self.fact_trades.empty:
            fact_nulls = self.fact_trades[
                ["trade_id", "account_id", "instrument_id"]
            ].isnull().any()
            if fact_nulls.any():
                logger.error("Found nulls in fact_trades keys")
                raise ValueError("Null values in fact table keys")

        logger.info("Transformed data validation passed")

    def get_transformed_dataframes(self) -> Dict:
        """Return all transformed DataFrames"""
        return {
            "dim_accounts": self.dim_accounts,
            "dim_instruments": self.dim_instruments,
            "dim_exchanges": self.dim_exchanges,
            "dim_dates": self.dim_dates,
            "fact_trades": self.fact_trades,
            "fact_daily_holdings": self.fact_daily_holdings,
            "fact_daily_account_summary": self.fact_daily_account_summary,
        }


def run_transformation(etl_run_id: str) -> Dict:
    """
    Main entry point for transformation

    Args:
        etl_run_id: Airflow DAG run ID

    Returns:
        Dictionary with transformation statistics
    """
    logger.info(f"=== DATA TRANSFORMATION STARTED (Run: {etl_run_id}) ===")

    try:
        transformer = DataTransformer(etl_run_id)
        stats = transformer.transform_all_data()

        logger.info(f"=== DATA TRANSFORMATION COMPLETED ===")
        logger.info(f"Transformation Summary: {stats}")

        return {
            "stats": stats,
            "dataframes": transformer.get_transformed_dataframes(),
        }

    except Exception as e:
        logger.error(f"=== DATA TRANSFORMATION FAILED ===", exc_info=True)
        raise


if __name__ == "__main__":
    # For local testing
    logging.basicConfig(
        level=logging.INFO,
        format="[%(asctime)s] [%(name)s] [%(levelname)s] %(message)s",
    )

    test_run_id = f"test_transform_{datetime.utcnow().isoformat()}"
    result = run_transformation(test_run_id)
    print("\n" + "=" * 60)
    print("Transformation Complete")
    print("=" * 60)
    print(result["stats"])
