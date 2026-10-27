import os
from pathlib import Path
import subprocess

import pytest


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "analytics" / "run_etl.sh"


@pytest.fixture
def run_script(tmp_path):
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    log = tmp_path / "commands"
    docker = bin_dir / "docker"
    docker.write_text(
        """#!/usr/bin/env bash
echo "$PWD|$(basename "$0") $*" >> "$COMMAND_LOG"
if [[ "$*" == "compose version" ]]; then
    exit "${COMPOSE_VERSION_STATUS:-0}"
fi
if [[ "$1" == compose ]]; then shift; fi
case "$1" in
    build) exit "${BUILD_STATUS:-0}" ;;
    exec)
        if [[ "${DB_DELAY:-0}" == 1 && ! -f "$COMMAND_LOG.db" ]]; then
            touch "$COMMAND_LOG.db"
            exit 1
        fi
        exit "${DB_STATUS:-0}"
        ;;
    run)
        if [[ "${BUSY_ONCE:-0}" == 1 && ! -f "$COMMAND_LOG.busy" ]]; then
            touch "$COMMAND_LOG.busy"
            exit 2
        fi
        exit "${PIPELINE_STATUS:-0}"
        ;;
esac
"""
    )
    docker.chmod(0o755)
    (bin_dir / "docker-compose").symlink_to(docker)
    sleep = bin_dir / "sleep"
    sleep.write_text("#!/usr/bin/env bash\nexit 0\n")
    sleep.chmod(0o755)

    def run(**settings):
        env = dict(os.environ, PATH=f"{bin_dir}:{os.environ['PATH']}", COMMAND_LOG=str(log))
        env.update(settings)
        result = subprocess.run(
            ["bash", str(SCRIPT)], cwd=tmp_path, env=env,
            capture_output=True, text=True, timeout=10,
        )
        return result, log.read_text().splitlines()

    return run


@pytest.mark.parametrize("legacy", [False, True])
def test_full_pipeline_from_another_directory(run_script, legacy):
    result, commands = run_script(COMPOSE_VERSION_STATUS="1" if legacy else "0")

    assert result.returncode == 0, result.stderr
    compose = "docker-compose" if legacy else "docker compose"
    assert commands[-3:] == [
        f"{ROOT}|{compose} build etl",
        f"{ROOT}|{compose} exec -T db pg_isready -U paysprint -d paysprint",
        f"{ROOT}|{compose} run --rm --no-deps -T etl python etl/run_pipeline.py",
    ]
    assert "OLAP tables and P&L views are ready" in result.stdout


def test_waits_for_postgres_and_retries_busy_refresh(run_script):
    result, commands = run_script(DB_DELAY="1", BUSY_ONCE="1")

    assert result.returncode == 0, result.stderr
    assert sum(" exec " in command for command in commands) == 2
    assert sum(" run " in command for command in commands) == 2


@pytest.mark.parametrize(
    "settings,expected_status,expected_runs,message",
    [
        ({"BUILD_STATUS": "7"}, 7, 0, ""),
        ({"DB_STATUS": "1"}, 1, 0, "PostgreSQL did not become ready"),
        ({"PIPELINE_STATUS": "1"}, 1, 1, "Analytics ETL failed"),
        ({"PIPELINE_STATUS": "2"}, 2, 60, "Analytics ETL remained busy"),
    ],
)
def test_failures_stop_the_script(run_script, settings, expected_status, expected_runs, message):
    result, commands = run_script(**settings)

    assert result.returncode == expected_status
    assert sum(" run " in command for command in commands) == expected_runs
    assert message in result.stderr
    assert "OLAP tables and P&L views are ready" not in result.stdout


def test_jenkins_populates_analytics_before_tests():
    pipeline = (ROOT / "Jenkinsfile").read_text()
    stages = [
        "stage('Start Application Stack')",
        "stage('Populate Analytics')",
        "stage('Backend Tests')",
        "stage('Frontend Tests')",
        "stage('Smoke Test')",
    ]
    positions = [pipeline.index(stage) for stage in stages]

    assert positions == sorted(positions)
    assert pipeline.index("docker-compose up -d --build") < pipeline.index("bash analytics/run_etl.sh")
