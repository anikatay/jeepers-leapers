# Jeepers Leapers

## Setup

```bash
# 1. Clone the repo
git clone https://github.com/anikatay/jeepers-leapers.git
cd jeepers-leapers
```

## Running With Docker Compose

1. Run:

   ```bash
   docker-compose up -d --build
   ```

   * If app does not rebuild with new changes because Docker is running the cached app, run:

     ```bash
     docker-compose up -d --build --force-recreate
     ```

2. Check that all containers are up and running:

   ```bash
   docker ps
   ```

3. If all containers are running, the front end is accessible from:

   ```text
   "your-ip":8090
   ```

## AI Trade Analysis

Reviews any trade on request with OpenAI and returns a verdict (`GOOD`, `MIXED`, `POOR` or
`TOO_EARLY`) with reasons. The spec, covering the API contract, the data sent to the model, limits
and acceptance criteria, is in [docs/specs/trade-analysis.md](docs/specs/trade-analysis.md).

```bash
# What the model would be told (no OpenAI call)
curl http://localhost:8095/api/trades/c3000000-0000-0000-0000-000000000006/analysis/context
# The review (about 2 to 4 seconds)
curl -X POST http://localhost:8095/api/trades/c3000000-0000-0000-0000-000000000006/analysis
```

### Configuration

Set these in `.env` (see `.env.example`); `docker-compose` passes them on.

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `OPENAI_API_KEY` | none | Required for reviews; without it the app starts and reviews return 502 |
| `OPENAI_MODEL` | `gpt-4o-mini` | Model used for reviews |
| `ETL_INTERVAL_SECONDS` | `600` | How often the `etl` service refreshes P&L |

### Usage log

```bash
docker-compose logs app | grep "analysis tradeId"
```

### Testing

```bash
mvn test                                                                         # offline, no key needed
env $(grep -E '^OPENAI_' .env | xargs) mvn test -Dtest=OpenAiTradeAnalyzerLiveTest   # 3 real calls
env $(grep -E '^OPENAI_' .env | xargs) mvn test -Dtest=TradeAnalysisEvalTest        # about 30 real calls
```

Run the evaluation after changing the prompt or model (`OPENAI_MODEL=...` to try another).

### Resetting inconsistent dev data

Databases seeded before the seeder fix have trades that never moved holdings or cash. To start clean:

```bash
docker-compose down -v
docker-compose up -d --build
python3 analytics/etl/run_pipeline.py --seed --trades 200 --start-date 2026-08-01
```

## Running the Frontend

1. Go into the frontend directory:

   ```bash
   cd frontend
   ```

2. Install dependencies:

   ```bash
   npm install
   ```

3. Start the frontend:

   ```bash
   npm run start
   ```

## Jupyter Setup

```bash
/usr/bin/python3 -m ensurepip --upgrade
pip3 install notebook
jupyter notebook
```

## Project Structure

```text
jeepers-leapers/
├── frontend/
├── src/
│   ├── main/
│   │   ├── java/com/neueda/leap/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── model/
│   │   │   ├── repository/
│   │   │   └── Main.java
│   │   └── resources/
│   └── test/java/com/neueda/leap/
```

## Key Dependencies

| Dependency     | Purpose            |
| -------------- | ------------------ |
| Java 21        | Backend            |
| Maven 3.9      | Build backend      |
| PostgreSQL     | Database           |
| Node.js 10.9.8 | Build frontend     |
| Angular 21.2.0 | Frontend framework |
| Docker         | Containerization   |
