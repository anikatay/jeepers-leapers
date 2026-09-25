"""
Airflow DAG: Daily Analytics ETL Pipeline

SIMPLIFIED FOR TESTING: Currently only Extract phase
Other phases (transform, load, validate) will be added after Extract is working
"""

from datetime import datetime, timedelta
from typing import Any, Dict
import logging

from airflow import DAG
from airflow.operators.python import PythonOperator
from airflow.operators.bash import BashOperator
from airflow.utils.task_group import TaskGroup

# Import ETL modules
import sys
sys.path.insert(0, '/opt/airflow')

from etl.extract import run_extraction

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
    "analytics_daily_etl_extract_only",
    default_args=default_args,
    description="[TESTING] Extract phase only - OLTP → Staging",
    schedule_interval="0 1 * * *",  # 01:00 UTC nightly
    catchup=False,
    max_active_runs=1,
    tags=["analytics", "etl", "testing"],
    doc_md="""
    # Analytics Daily ETL - EXTRACT PHASE ONLY (Testing)
    
    **Current Status**: Testing extract phase in isolation
    
    **Purpose**: Extract data from OLTP database to staging schema
    
    **Schedule**: Nightly at 01:00 UTC
    
    **TODO**: Add transform, validate, load phases after extract is stable
    """,
)

# ============================================================================
# HELPER FUNCTIONS
# ============================================================================


def create_etl_run_id(**context) -> str:
    """Create unique ETL run ID (DAG run ID)"""
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
        stats = run_extraction(etl_run_id)
        context["task_instance"].xcom_push(key="extraction_stats", value=stats)
        logger.info(f"Extraction successful: {stats}")
        return stats
    except Exception as e:
        logger.error(f"Extraction failed: {str(e)}", exc_info=True)
        raise


# ============================================================================
# DAG TASKS (EXTRACT ONLY - TESTING)
# ============================================================================

with dag:
    # Setup phase
    with TaskGroup("setup", tooltip="Setup and validation") as setup_tg:
        setup_create_run_id = PythonOperator(
            task_id="create_run_id",
            python_callable=create_etl_run_id,
            provide_context=True,
        )

        setup_verify_schemas = BashOperator(
            task_id="verify_schemas",
            bash_command="""
            psql -h ${DB_HOST} -p ${DB_PORT} -U ${DB_USER} -d ${DB_NAME} -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name IN ('analytics', 'staging');"
            """,
            env={
                "DB_HOST": "db",
                "DB_PORT": "5432",
                "DB_USER": "paysprint",
                "DB_NAME": "paysprint",
            },
            do_xcom_push=False,
        )

        setup_create_run_id >> setup_verify_schemas

    # Extract phase
    extract = PythonOperator(
        task_id="extract",
        python_callable=extract_task,
        provide_context=True,
        retries=3,
        retry_delay=timedelta(minutes=5),
    )

    # DAG dependencies (simple for testing)
    setup_tg >> extract
