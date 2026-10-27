# Runs the ETL pipeline on a fixed interval so analytics P&L stays current.
# numpy 1.24 in requirements.txt has no wheels for Python 3.12, so stay on 3.11.
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY api.py .
COPY etl/ etl/
COPY schema/ schema/

# extract_etl.py appends to etl.log in the working directory
RUN useradd --system etl && chown etl /app
USER etl

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    ETL_INTERVAL_SECONDS=600

EXPOSE 8094

CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8094"]
