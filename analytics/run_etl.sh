#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

if docker compose version >/dev/null 2>&1; then
    compose=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
    compose=(docker-compose)
else
    echo "Docker Compose is required. Install it and start the application stack first." >&2
    exit 1
fi

# The image supplies Python 3.11, pip, and analytics/requirements.txt.
"${compose[@]}" build etl

echo "Waiting for PostgreSQL..."
postgres_ready=false
for ((attempt = 1; attempt <= 60; attempt++)); do
    if "${compose[@]}" exec -T db pg_isready -U paysprint -d paysprint; then
        postgres_ready=true
        break
    fi
    sleep 2
done
if [[ "$postgres_ready" != true ]]; then
    echo "PostgreSQL did not become ready. Start the stack with Docker Compose first." >&2
    exit 1
fi

# A scheduled refresh may already hold the pipeline's advisory lock.
for ((attempt = 1; attempt <= 60; attempt++)); do
    echo "Running analytics ETL (attempt $attempt/60)..."
    if "${compose[@]}" run --rm --no-deps -T etl python etl/run_pipeline.py; then
        echo "Analytics ETL complete. OLAP tables and P&L views are ready."
        exit 0
    else
        status=$?
    fi
    if [[ "$status" -ne 2 ]]; then
        echo "Analytics ETL failed (exit $status)." >&2
        exit "$status"
    fi
    echo "Another analytics refresh is running; retrying in 2 seconds..."
    sleep 2
done

echo "Analytics ETL remained busy after 60 attempts." >&2
exit 2
