# Runs the ETL pipeline on a fixed interval so analytics P&L stays current.
# numpy 1.24 in requirements.txt has no wheels for Python 3.12, so stay on 3.11.
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY etl/ etl/
COPY schema/ schema/

# extract_etl.py appends to etl.log in the working directory
RUN useradd --system etl && chown etl /app
USER etl

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    ETL_INTERVAL_SECONDS=600

# Each run prints its JSON result on stdout; a failed or busy run is retried next interval.
CMD ["sh", "-c", "while true; do python etl/run_pipeline.py --json; sleep \"$ETL_INTERVAL_SECONDS\"; done"]
