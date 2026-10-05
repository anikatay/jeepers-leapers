"""
Complete ETL Pipeline Orchestrator

Runs all phases in correct order:
  Phase 0: Schema initialization
  Phase 1: Data extraction
  Phase 3: P&L transformation

Usage:
  python3 run_pipeline.py                          # Run all phases with 50 test trades
  python3 run_pipeline.py --trades 100             # Custom number of trades
  python3 run_pipeline.py --skip-seed              # Skip trade seeding (use existing OLTP data)
  python3 run_pipeline.py --phase 1                # Run only Phase 1
  python3 run_pipeline.py --phase 3 --skip-seed    # Run only Phase 3 (assumes Phase 0+1 complete)
"""

import sys
import os
import argparse
import logging
from datetime import datetime
from typing import Dict, Any

# Add parent directory to path so we can import etl module
# This allows running as: python3 etl/run_pipeline.py
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | %(levelname)-8s | %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S'
)
logger = logging.getLogger(__name__)


def run_phase_0() -> Dict[str, Any]:
    """
    Phase 0: Initialize staging schema and tables.
    
    Creates:
      - staging schema
      - 5 raw tables (exchanges, accounts, instruments, holdings, trades)
    """
    logger.info("="*80)
    logger.info("PHASE 0: SCHEMA INITIALIZATION")
    logger.info("="*80)
    
    try:
        from etl.schema_init import initialize_database
        
        logger.info("Creating staging schema and tables...")
        initialize_database()
        logger.info("✅ Phase 0 complete: Staging schema initialized")
        
        return {
            'status': 'success',
            'phase': 0,
            'message': 'Staging schema created'
        }
    except Exception as e:
        logger.error(f"❌ Phase 0 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 0,
            'error': str(e)
        }


def run_phase_seed(num_trades: int = 50, start_date: str = "2025-08-25") -> Dict[str, Any]:
    """
    Pre-Phase 1: Seed test trade data into OLTP.
    
    Generates realistic trades for testing.
    """
    logger.info("="*80)
    logger.info("SEEDING: TEST TRADE DATA")
    logger.info("="*80)
    
    try:
        from etl.seed_trades import seed_trades_main
        
        logger.info(f"Generating {num_trades} test trades...")
        result = seed_trades_main(num_trades=num_trades, start_date=start_date)
        
        logger.info(f"✅ Seed complete: {result['inserted']} trades inserted")
        return {
            'status': 'success',
            'phase': 'seed',
            'trades_inserted': result['inserted'],
            'message': f"Seeded {result['inserted']} trades"
        }
    except Exception as e:
        logger.error(f"❌ Seed failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 'seed',
            'error': str(e)
        }


def run_phase_1() -> Dict[str, Any]:
    """
    Phase 1: Extract data from OLTP to staging.
    
    Extracts:
      - exchanges (2 rows)
      - accounts (5 rows)
      - instruments (7 rows)
      - holdings (8 rows)
      - trades (from last 90 days)
    """
    logger.info("="*80)
    logger.info("PHASE 1: DATA EXTRACTION")
    logger.info("="*80)
    
    try:
        from etl.extract_etl import run_etl
        
        logger.info("Extracting data from OLTP to staging...")
        result = run_etl()
        
        logger.info("Extraction results:")
        for table, (extracted, loaded) in result.items():
            logger.info(f"  {table:15} | extracted: {extracted:>3} | loaded: {loaded:>3}")
        
        total_rows = sum(loaded for _, (_, loaded) in result.items())
        logger.info(f"✅ Phase 1 complete: {total_rows} total rows loaded")
        
        return {
            'status': 'success',
            'phase': 1,
            'extraction_results': result,
            'total_rows': total_rows
        }
    except Exception as e:
        logger.error(f"❌ Phase 1 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 1,
            'error': str(e)
        }


def run_phase_3() -> Dict[str, Any]:
    """
    Phase 3: Calculate P&L using FIFO matching.
    
    Reads trades from staging, applies FIFO algorithm,
    and loads results to fact_trades table.
    """
    logger.info("="*80)
    logger.info("PHASE 3: P&L TRANSFORMATION (FIFO)")
    logger.info("="*80)
    
    try:
        from etl.transform_pnl import run_pnl_transformation
        
        run_id = f"pipeline-run-{datetime.now().strftime('%Y%m%d-%H%M%S')}"
        logger.info(f"Running P&L transformation (run_id: {run_id})...")
        result = run_pnl_transformation(run_id)
        
        logger.info("P&L transformation results:")
        logger.info(f"  Status:           {result.get('status', 'N/A')}")
        logger.info(f"  Trades processed: {result.get('trades_processed', 0)}")
        logger.info(f"  Trades loaded:    {result.get('trades_loaded', 0)}")
        logger.info(f"  Matched trades:   {result.get('matched_trades', 0)}")
        logger.info(f"  Orphan trades:    {result.get('orphan_trades', 0)}")
        
        if result.get('error'):
            logger.warning(f"  Error:            {result['error']}")
        
        logger.info(f"✅ Phase 3 complete: {result.get('trades_loaded', 0)} P&L records loaded")
        
        return {
            'status': result.get('status', 'unknown'),
            'phase': 3,
            'transformation_results': result
        }
    except Exception as e:
        logger.error(f"❌ Phase 3 failed: {str(e)}")
        return {
            'status': 'failed',
            'phase': 3,
            'error': str(e)
        }


def main():
    """Main orchestrator function."""
    parser = argparse.ArgumentParser(
        description='ETL Pipeline Orchestrator - Run all phases in order',
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python3 run_pipeline.py                    # Run all phases with 50 trades
  python3 run_pipeline.py --trades 100       # Use 100 test trades
  python3 run_pipeline.py --skip-seed        # Use existing OLTP data (no seeding)
  python3 run_pipeline.py --phase 1          # Run only Phase 1
  python3 run_pipeline.py --phase 3          # Run only Phase 3 (assumes Phase 0+1 done)
        """
    )
    
    parser.add_argument(
        '--phase',
        type=int,
        choices=[0, 1, 3],
        default=None,
        help='Run only a specific phase (0, 1, or 3). Default: run all phases'
    )
    
    parser.add_argument(
        '--trades',
        type=int,
        default=50,
        help='Number of test trades to generate (default: 50)'
    )
    
    parser.add_argument(
        '--skip-seed',
        action='store_true',
        help='Skip trade seeding (use existing OLTP data)'
    )
    
    parser.add_argument(
        '--start-date',
        type=str,
        default='2025-08-25',
        help='Start date for generated trades (YYYY-MM-DD, default: 2025-08-25)'
    )
    
    args = parser.parse_args()
    
    # Track results
    all_results = []
    pipeline_status = 'success'
    
    try:
        # Phase 0 (always run unless specific phase requested)
        if args.phase is None or args.phase == 0:
            result = run_phase_0()
            all_results.append(result)
            if result['status'] != 'success':
                pipeline_status = 'failed'
                logger.error("Cannot proceed without Phase 0. Exiting.")
                sys.exit(1)
        
        # Seed trades (if not skipped)
        if not args.skip_seed:
            result = run_phase_seed(num_trades=args.trades, start_date=args.start_date)
            all_results.append(result)
            if result['status'] != 'success':
                logger.warning("Seed failed, but continuing with existing OLTP data...")
        
        # Phase 1
        if args.phase is None or args.phase == 1:
            result = run_phase_1()
            all_results.append(result)
            if result['status'] != 'success':
                pipeline_status = 'failed'
                logger.error("Phase 1 failed. Cannot proceed to Phase 3.")
                if args.phase is None:  # Only exit if running all phases
                    sys.exit(1)
        
        # Phase 3
        if args.phase is None or args.phase == 3:
            result = run_phase_3()
            all_results.append(result)
            if result['status'] != 'success':
                pipeline_status = 'failed'
    
    except KeyboardInterrupt:
        logger.warning("\n⚠️  Pipeline interrupted by user")
        pipeline_status = 'interrupted'
    except Exception as e:
        logger.error(f"Unexpected error: {str(e)}")
        pipeline_status = 'error'
    
    # Final summary
    logger.info("="*80)
    logger.info("PIPELINE SUMMARY")
    logger.info("="*80)
    
    for result in all_results:
        phase = result.get('phase', 'unknown')
        status = result.get('status', 'unknown').upper()
        symbol = '✅' if status == 'SUCCESS' else '❌'
        logger.info(f"{symbol} Phase {phase}: {status}")
        if result.get('error'):
            logger.info(f"   Error: {result['error']}")
    
    logger.info("="*80)
    if pipeline_status == 'success':
        logger.info(f"✅ PIPELINE COMPLETE - Status: {pipeline_status.upper()}")
        logger.info("\n📊 Next steps:")
        logger.info("   1. Query analytics.fact_trades to verify P&L data:")
        logger.info("      SELECT * FROM analytics.fact_trades LIMIT 20;")
        logger.info("   2. Run unit tests:")
        logger.info("      pytest tests/test_transform_pnl.py -v")
    else:
        logger.info(f"❌ PIPELINE FAILED - Status: {pipeline_status.upper()}")
    logger.info("="*80)
    
    return 0 if pipeline_status == 'success' else 1


if __name__ == "__main__":
    sys.exit(main())
