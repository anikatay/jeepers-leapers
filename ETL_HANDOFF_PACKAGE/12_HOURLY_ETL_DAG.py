"""
Airflow DAG: Hourly Trading Analytics ETL Pipeline

Runs 5 sequential ETL modules hourly:
:00 → Extract
:15 → P&L Transform
:30 → Holdings Transform
:45 → Summary Transform
:55 → Validate

Schedule: Every hour, 24 times per day
Retries: 3 with exponential backoff (5, 10, 20 minutes)
Max Active Runs: 1 (no parallel runs)
"""

from datetime import datetime, timedelta
from typing import Any, Dict
import logging

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.utils.task_group import TaskGroup

# Import ETL modules
from etl.extract_etl import run_extract
from etl.transform_pnl_etl import run_pnl_etl
from etl.transform_holdings_etl import run_holdings_etl
from etl.transform_account_summary_etl import run_summary_etl
from etl.validate_etl import run_validate

logger = logging.getLogger(__name__)

# ============================================================================
# DAG CONFIGURATION
# ============================================================================

default_args = {
    "owner": "data_engineering",
    "depends_on_past": False,
    "start_date": datetime(2026, 1, 1),
    "email": ["data-alerts@company.com"],
    "email_on_failure": True,
    "email_on_retry": False,
    "retries": 3,
    "retry_delay": timedelta(minutes=5),
    "retry_exponential_base": 2,  # Exponential backoff: 5, 10, 20 minutes
}

dag = DAG(
    "trading_analytics_hourly_etl",
    default_args=default_args,
    description="Hourly ETL pipeline: Extract → P&L → Holdings → Summary → Validate",
    schedule_interval="0 * * * *",  # Every hour at :00
    catchup=False,
    max_active_runs=1,
    tags=["analytics", "etl", "trading"],
    doc_md="""
    # Trading Analytics ETL - Hourly Pipeline
    
    **Purpose**: Transform raw trading data (OLTP) → Analytics schema (Star)
    
    **Schedule**: Every hour at :00
    
    **Modules**:
    1. Extract (:00) - OLTP → Staging
    2. P&L (:15) - Calculate FIFO P&L → fact_trades
    3. Holdings (:30) - Daily position snapshots → fact_daily_holdings
    4. Summary (:45) - Account aggregates → fact_daily_account_summary
    5. Validate (:55) - Quality checks & reconciliation
    
    **Total Duration**: ~15-20 minutes per hour
    
    **Error Handling**: 3 retries with exponential backoff
    """,
)

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================


def create_etl_run_id(**context) -> str:
    """Create unique ETL run ID (Airflow DAG run ID)"""
    run_id = context["run_id"]
    logger.info(f"Created ETL run ID: {run_id}")
    context["task_instance"].xcom_push(key="etl_run_id", value=run_id)
    return run_id


def extract_task(**context) -> Dict[str, Any]:
    """Extract task: Read from OLTP → staging"""
    etl_run_id = context["task_instance"].xcom_pull(
        task_ids="setup.create_run_id", key="etl_run_id"
    )

    logger.info(f"Starting extraction (Run: {etl_run_id})")

    try:
        stats = run_extract(etl_run_id)
        context["task_instance"].xcom_push(key="extraction_stats", value=stats)
        logger.info(f"Extraction successful: {stats}")
        return stats
    except Exception as e:
        logger.error(f"Extraction failed: {str(e)}", exc_info=True)
        raise


def pnl_task(**context) -> Dict[str, Any]:
    """P&L task: Calculate FIFO P&L → fact_trades"""
    etl_run_id = context["task_instance"].xcom_pull(
        task_ids="setup.create_run_id", key="etl_run_id"
    )

    logger.info(f"Starting P&L transformation (Run: {etl_run_id})")

    try:
        stats = run_pnl_etl(etl_run_id)
        context["task_instance"].xcom_push(key="pnl_stats", value=stats)
        logger.info(f"P&L transformation successful: {stats}")
        return stats
    except Exception as e:
        logger.error(f"P&L transformation failed: {str(e)}", exc_info=True)
        raise


def holdings_task(**context) -> Dict[str, Any]:
    """Holdings task: Daily position snapshots → fact_daily_holdings"""
    etl_run_id = context["task_instance"].xcom_pull(
        task_ids="setup.create_run_id", key="etl_run_id"
    )

    logger.info(f"Starting holdings transformation (Run: {etl_run_id})")

    try:
        stats = run_holdings_etl(etl_run_id)
        context["task_instance"].xcom_push(key="holdings_stats", value=stats)
        logger.info(f"Holdings transformation successful: {stats}")
        return stats
    except Exception as e:
        logger.error(f"Holdings transformation failed: {str(e)}", exc_info=True)
        raise


def summary_task(**context) -> Dict[str, Any]:
    """Summary task: Account aggregates → fact_daily_account_summary"""
    etl_run_id = context["task_instance"].xcom_pull(
        task_ids="setup.create_run_id", key="etl_run_id"
    )

    logger.info(f"Starting summary transformation (Run: {etl_run_id})")

    try:
        stats = run_summary_etl(etl_run_id)
        context["task_instance"].xcom_push(key="summary_stats", value=stats)
        logger.info(f"Summary transformation successful: {stats}")
        return stats
    except Exception as e:
        logger.error(f"Summary transformation failed: {str(e)}", exc_info=True)
        raise


def validate_task(**context) -> Dict[str, Any]:
    """Validate task: Quality checks & reconciliation"""
    etl_run_id = context["task_instance"].xcom_pull(
        task_ids="setup.create_run_id", key="etl_run_id"
    )

    logger.info(f"Starting validation (Run: {etl_run_id})")

    try:
        results = run_validate(etl_run_id)
        context["task_instance"].xcom_push(key="validation_results", value=results)
        logger.info(f"Validation complete: {results}")
        return results
    except Exception as e:
        logger.error(f"Validation failed: {str(e)}", exc_info=True)
        raise


def log_completion(**context) -> str:
    """Log ETL completion"""
    etl_run_id = context["task_instance"].xcom_pull(
        task_ids="setup.create_run_id", key="etl_run_id"
    )

    extraction_stats = context["task_instance"].xcom_pull(
        task_ids="extract.extract_all_data", key="extraction_stats"
    )
    pnl_stats = context["task_instance"].xcom_pull(
        task_ids="transform.pnl_etl", key="pnl_stats"
    )
    holdings_stats = context["task_instance"].xcom_pull(
        task_ids="transform.holdings_etl", key="holdings_stats"
    )
    summary_stats = context["task_instance"].xcom_pull(
        task_ids="transform.summary_etl", key="summary_stats"
    )
    validation_results = context["task_instance"].xcom_pull(
        task_ids="validate.run_validation", key="validation_results"
    )

    logger.info("=" * 80)
    logger.info(f"ETL RUN COMPLETED: {etl_run_id}")
    logger.info("=" * 80)
    logger.info(f"Extraction: {extraction_stats}")
    logger.info(f"P&L Transform: {pnl_stats}")
    logger.info(f"Holdings Transform: {holdings_stats}")
    logger.info(f"Summary Transform: {summary_stats}")
    logger.info(f"Validation: {validation_results}")
    logger.info("=" * 80)

    return f"ETL Run {etl_run_id} completed successfully"


# ============================================================================
# DAG TASKS
# ============================================================================

with dag:
    # ========================================================================
    # SETUP PHASE
    # ========================================================================
    with TaskGroup("setup", tooltip="Setup and validation") as setup_tg:
        setup_create_run_id = PythonOperator(
            task_id="create_run_id",
            python_callable=create_etl_run_id,
            provide_context=True,
        )

    # ========================================================================
    # EXTRACT PHASE
    # ========================================================================
    with TaskGroup("extract", tooltip="Extract OLTP → Staging") as extract_tg:
        extract_all_data = PythonOperator(
            task_id="extract_all_data",
            python_callable=extract_task,
            provide_context=True,
        )

    # ========================================================================
    # TRANSFORM PHASE (3 parallel transforms)
    # ========================================================================
    with TaskGroup("transform", tooltip="Transform Staging → Analytics") as transform_tg:
        pnl_etl = PythonOperator(
            task_id="pnl_etl",
            python_callable=pnl_task,
            provide_context=True,
        )

        holdings_etl = PythonOperator(
            task_id="holdings_etl",
            python_callable=holdings_task,
            provide_context=True,
        )

        summary_etl = PythonOperator(
            task_id="summary_etl",
            python_callable=summary_task,
            provide_context=True,
        )

        # Dependencies within transform
        pnl_etl >> holdings_etl >> summary_etl

    # ========================================================================
    # VALIDATE PHASE
    # ========================================================================
    with TaskGroup("validate", tooltip="Validate and reconcile") as validate_tg:
        run_validation = PythonOperator(
            task_id="run_validation",
            python_callable=validate_task,
            provide_context=True,
        )

    # ========================================================================
    # CLEANUP PHASE
    # ========================================================================
    with TaskGroup("cleanup", tooltip="Logging and notifications") as cleanup_tg:
        log_completion_task = PythonOperator(
            task_id="log_completion",
            python_callable=log_completion,
            provide_context=True,
        )

    # ========================================================================
    # DAG DEPENDENCIES
    # ========================================================================
    setup_tg >> extract_tg >> transform_tg >> validate_tg >> cleanup_tg
