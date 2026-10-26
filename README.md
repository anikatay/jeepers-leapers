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

### Market sector and heatmap endpoints

The market sidecar maintains a configured US large-cap universe with ten tickers
per standard sector. The backend exposes the catalog and combines it with the
same Redis-backed quote cache used by instrument prices:

```bash
API_URL=http://localhost:8091
TOKEN=$(curl -sS -X POST "$API_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com","password":"123"}' | jq -r .token)

# Sector catalog and ranked tickers
curl -sS "$API_URL/api/market/sectors" \
  -H "Authorization: Bearer $TOKEN" | jq

# One sector
curl -sS "$API_URL/api/market/sectors/information-technology" \
  -H "Authorization: Bearer $TOKEN" | jq

# All sectors with cached prices and quote status
curl -sS "$API_URL/api/market/heatmap" \
  -H "Authorization: Bearer $TOKEN" | jq

# One sector's heatmap data
curl -sS "$API_URL/api/market/heatmap?sector=energy" \
  -H "Authorization: Bearer $TOKEN" | jq
```

The heatmap loads one month of daily performance history through the backend
batch history endpoint; requests are split into groups of 50 to match the
market sidecar's upstream limit.

Each heatmap ticker has `LIVE`, `STALE`, `CLOSED`, or `UNAVAILABLE` status.
`UNAVAILABLE` means the sidecar has not delivered a quote for that ticker yet.
The current universe is versioned in the response and uses the configured
large-cap order as its ranking basis; it is not an automatic market-cap
screening service.

The sidecar endpoints are also available directly for diagnostics:

```bash
curl -sS http://localhost:8093/sectors | jq
curl -sS http://localhost:8093/sectors/energy | jq
curl -sS http://localhost:8093/health | jq
```

`SECTOR_STREAM_ENABLED=true` keeps the sector tickers persistently subscribed
to the sidecar stream. Set it to `false` when running the sidecar without the
background sector stream.

## Analyst dashboard loading

The analyst dashboard initially loads its instrument catalog and trade snapshot
without fetching market history for the full catalog. Chart history is requested
separately for the two initially selected instruments, then on demand when an
instrument is selected. Loaded histories are reused until the dashboard reloads;
chart loading and retry errors are displayed independently of dashboard data.

## AI Trade Analysis

Reviews any trade on request with OpenAI and returns a verdict (`GOOD`, `MIXED`, `POOR` or
`TOO_EARLY`) with reasons. The spec, covering the API contract, the data sent to the model, limits
and acceptance criteria, is in [docs/specs/trade-analysis.md](docs/specs/trade-analysis.md).

Both endpoints need a login token and only work on the logged-in user's own trades. The seeded user
`bob@example.com` (password `123`) owns trade `c3000000-0000-0000-0000-000000000007`:

```bash
TOKEN=$(curl -s -X POST http://localhost:8091/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com","password":"123"}' | jq -r .token)
# What the model would be told (no OpenAI call)
curl -H "Authorization: Bearer $TOKEN" http://localhost:8091/api/trades/c3000000-0000-0000-0000-000000000007/analysis/context
# The review (about 2 to 4 seconds)
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8091/api/trades/c3000000-0000-0000-0000-000000000007/analysis
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
