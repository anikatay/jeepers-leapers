"""
Unit tests for the pipeline orchestrator (run_pipeline.py)

Tests verify:
1. A phase that fails is reported as failed, never as success
2. Test trades are only seeded into OLTP when --seed is passed
3. run_pipeline() returns a result instead of exiting, stops at the first
   failed phase, and reports 'busy' when another run holds the lock
4. run_etl() reports failure when a table errored during extraction

All database access is mocked.
"""

import json
import os
import sys
from contextlib import contextmanager
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest

# Add parent directory to path for imports
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from etl import run_pipeline
from etl.extract_etl import run_etl


EXTRACTION_STATS = {
    'exchanges': (2, 2),
    'accounts': (5, 5),
    'instruments': (7, 7),
    'holdings': (8, 8),
    'trades': (18, 18),
}


def _phase_ok(phase):
    return {'status': 'success', 'phase': phase}


def _lock(acquired=True):
    """Stand-in for pipeline_lock() that never touches a database."""
    @contextmanager
    def fake_lock():
        yield acquired
    return fake_lock


# ============================================================================
# PHASE STATUS
# ============================================================================

class TestPhaseStatus:
    """A phase must not report success when the work underneath failed"""

    def test_phase_0_fails_when_initialization_returns_false(self):
        with patch('etl.schema_init.initialize_database', return_value=False):
            result = run_pipeline.run_phase_0()

        assert result['status'] == 'failed'
        assert result['error']

    def test_phase_0_succeeds_when_initialization_returns_true(self):
        with patch('etl.schema_init.initialize_database', return_value=True):
            result = run_pipeline.run_phase_0()

        assert result['status'] == 'success'

    @pytest.mark.parametrize('etl_status', ['partial', 'failed'])
    def test_phase_1_fails_unless_extraction_fully_succeeds(self, etl_status):
        etl_result = {
            'status': etl_status,
            'run_id': 'run-1',
            'extraction_stats': dict(EXTRACTION_STATS, trades=(0, 0)),
            'failed_tables': ['trades'],
        }
        with patch.object(run_pipeline, 'wait_for_oltp'), \
             patch('etl.extract_etl.run_etl', return_value=etl_result):
            result = run_pipeline.run_phase_1()

        assert result['status'] == 'failed'
        assert 'trades' in result['error']

    def test_phase_1_succeeds_on_full_extraction(self):
        etl_result = {
            'status': 'success',
            'run_id': 'run-1',
            'extraction_stats': EXTRACTION_STATS,
            'failed_tables': [],
        }
        with patch.object(run_pipeline, 'wait_for_oltp'), \
             patch('etl.extract_etl.run_etl', return_value=etl_result):
            result = run_pipeline.run_phase_1()

        assert result['status'] == 'success'
        assert result['total_rows'] == 40

    def test_phase_1_fails_when_oltp_never_becomes_ready(self):
        with patch.object(run_pipeline, 'wait_for_oltp',
                          side_effect=RuntimeError("OLTP tables not found")), \
             patch('etl.extract_etl.run_etl') as mock_run_etl:
            result = run_pipeline.run_phase_1()

        assert result['status'] == 'failed'
        assert 'OLTP tables not found' in result['error']
        mock_run_etl.assert_not_called()

    def test_phase_3_passes_through_transform_failure(self):
        transform_result = {'status': 'failed', 'error': 'check constraint violated'}
        with patch('etl.transform_pnl.run_pnl_transformation', return_value=transform_result):
            result = run_pipeline.run_phase_3()

        assert result['status'] == 'failed'
        assert result['error'] == 'check constraint violated'


# ============================================================================
# SEEDING
# ============================================================================

class TestSeeding:
    """Seeding writes test data into OLTP, so it must be asked for explicitly"""

    def _run_main(self, argv):
        with patch.object(sys, 'argv', ['run_pipeline.py'] + argv), \
             patch.object(run_pipeline, 'pipeline_lock', _lock()), \
             patch.object(run_pipeline, 'run_phase_0', return_value=_phase_ok(0)), \
             patch.object(run_pipeline, 'run_phase_1', return_value=_phase_ok(1)), \
             patch.object(run_pipeline, 'run_phase_2', return_value=_phase_ok(2)), \
             patch.object(run_pipeline, 'run_phase_3', return_value=_phase_ok(3)), \
             patch.object(run_pipeline, 'run_phase_seed',
                          return_value=_phase_ok('seed')) as mock_seed:
            exit_code = run_pipeline.main()
        return exit_code, mock_seed

    def test_default_run_does_not_seed(self):
        exit_code, mock_seed = self._run_main([])

        assert exit_code == 0
        mock_seed.assert_not_called()

    def test_single_phase_run_does_not_seed(self):
        exit_code, mock_seed = self._run_main(['--phase', '3'])

        assert exit_code == 0
        mock_seed.assert_not_called()

    def test_deprecated_skip_seed_flag_still_accepted(self):
        exit_code, mock_seed = self._run_main(['--skip-seed'])

        assert exit_code == 0
        mock_seed.assert_not_called()

    def test_seed_flag_seeds_with_requested_size(self):
        exit_code, mock_seed = self._run_main(['--seed', '--trades', '25'])

        assert exit_code == 0
        mock_seed.assert_called_once_with(num_trades=25, start_date='2025-08-25')


# ============================================================================
# RUN_PIPELINE (the entry point an API calls)
# ============================================================================

class TestRunPipeline:
    """run_pipeline() must hand back a result dict and never exit or raise"""
    
    def _patch_phases(self, phase_1=None, phase_3=None):
        transform = {'status': 'success', 'trades_loaded': 18, 'matched_trades': 8}
        return [
            patch.object(run_pipeline, 'run_phase_0', return_value=_phase_ok(0)),
            patch.object(run_pipeline, 'run_phase_1', return_value=phase_1 or _phase_ok(1)),
            patch.object(run_pipeline, 'run_phase_2', return_value=_phase_ok(2)),
            patch.object(run_pipeline, 'run_phase_3', return_value=phase_3 or dict(
                _phase_ok(3), transformation_results=transform)),
        ]
    
    def _run(self, patches, acquired=True, **kwargs):
        mocks = [p.start() for p in patches]
        try:
            with patch.object(run_pipeline, 'pipeline_lock', _lock(acquired)):
                return run_pipeline.run_pipeline(**kwargs), mocks
        finally:
            for p in patches:
                p.stop()
    
    def test_success_returns_all_phases_and_summary(self):
        outcome, _ = self._run(self._patch_phases())
        
        assert outcome['status'] == 'success'
        assert [r['phase'] for r in outcome['phases']] == [0, 1, 2, 3]
        assert outcome['summary']['trades_loaded'] == 18
        assert outcome['error'] is None
        assert outcome['duration_seconds'] >= 0
    
    def test_stops_at_first_failed_phase(self):
        failed_phase_1 = {'status': 'failed', 'phase': 1, 'error': 'OLTP tables not found'}
        outcome, mocks = self._run(self._patch_phases(phase_1=failed_phase_1))
        
        assert outcome['status'] == 'failed'
        assert outcome['error'] == 'OLTP tables not found'
        assert [r['phase'] for r in outcome['phases']] == [0, 1]
        assert outcome['summary'] is None
        mocks[2].assert_not_called()  # Phase 2
        mocks[3].assert_not_called()  # Phase 3
    
    def test_partial_phase_3_is_not_success(self):
        partial = {'status': 'partial', 'phase': 3, 'error': 'No trades in staging',
                   'transformation_results': {'status': 'partial'}}
        outcome, _ = self._run(self._patch_phases(phase_3=partial))
        
        assert outcome['status'] == 'failed'
        assert outcome['error'] == 'No trades in staging'
        assert outcome['summary'] is None
    
    def test_busy_when_another_run_holds_the_lock(self):
        outcome, mocks = self._run(self._patch_phases(), acquired=False)
        
        assert outcome['status'] == 'busy'
        assert outcome['phases'] == []
        for mock_phase in mocks:
            mock_phase.assert_not_called()
    
    def test_unreachable_analytics_database_is_failure_not_exception(self):
        @contextmanager
        def broken_lock():
            raise RuntimeError("Analytics database is not reachable")
            yield
        
        with patch.object(run_pipeline, 'pipeline_lock', broken_lock):
            outcome = run_pipeline.run_pipeline()
        
        assert outcome['status'] == 'failed'
        assert 'not reachable' in outcome['error']
    
    def test_single_phase_runs_only_that_phase(self):
        outcome, mocks = self._run(self._patch_phases(), phase=3)
        
        assert [r['phase'] for r in outcome['phases']] == [3]
        mocks[0].assert_not_called()


class TestCommandLine:
    """Exit codes and --json output, for callers that run the script as a process"""
    
    def _main(self, argv, outcome):
        with patch.object(sys, 'argv', ['run_pipeline.py'] + argv), \
             patch.object(run_pipeline, 'run_pipeline', return_value=outcome):
            return run_pipeline.main()
    
    def _outcome(self, status, error=None):
        return {'status': status, 'phases': [], 'summary': None,
                'error': error, 'duration_seconds': 0.1}
    
    @pytest.mark.parametrize('status,exit_code', [('success', 0), ('failed', 1), ('busy', 2)])
    def test_exit_code_per_status(self, status, exit_code):
        assert self._main([], self._outcome(status, error='x')) == exit_code
    
    def test_json_flag_prints_the_result(self, capsys):
        self._main(['--json'], self._outcome('success'))
        
        printed = json.loads(capsys.readouterr().out)
        assert printed['status'] == 'success'
        assert set(printed) == {'status', 'phases', 'summary', 'error', 'duration_seconds'}


# ============================================================================
# EXTRACTION FAILURES
# ============================================================================

class TestRunEtlFailedTables:
    """run_etl() must not hide a table that errored behind a (0, 0) row count"""

    def _read_sql(self, failing_table=None):
        def read_sql(query, engine):
            if failing_table and f"public.{failing_table}" in str(query):
                raise RuntimeError(f"relation public.{failing_table} does not exist")
            return pd.DataFrame({'id': [1, 2]})
        return read_sql

    def _run(self, failing_table=None):
        with patch('etl.extract_etl.validate_config', return_value=True), \
             patch('etl.extract_etl.get_oltp_engine', return_value=MagicMock()), \
             patch('etl.extract_etl.get_staging_engine', return_value=MagicMock()), \
             patch('etl.extract_etl.pd.read_sql', side_effect=self._read_sql(failing_table)), \
             patch.object(pd.DataFrame, 'to_sql', return_value=None):
            return run_etl()

    def test_all_tables_extracted_is_success(self):
        result = self._run()

        assert result['status'] == 'success'
        assert result['failed_tables'] == []

    def test_one_table_erroring_is_failure(self):
        result = self._run(failing_table='trades')

        assert result['status'] == 'failed'
        assert result['failed_tables'] == ['trades']
        assert result['extraction_stats']['trades'] == (0, 0)
        assert result['extraction_stats']['exchanges'] == (2, 2)
