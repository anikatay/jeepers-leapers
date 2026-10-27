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

3. Populate analytics after starting the containers (including after pulling updates):

   ```bash
   bash analytics/run_etl.sh
   ```

   This installs the ETL requirements inside its Docker image and runs the full
   OLTP-to-OLAP pipeline. No host Python or pip setup is required. Jenkins runs
   this script after starting the stack and before tests. See the
   [analytics guide](analytics/README.md) for details.

4. If all containers are running, the front end is accessible from:

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

The demo seed in `src/main/resources/data.sql` contains 30,000 trades from
January 2 through October 26, 2026, with at least 20 trades per US stock-market
session. Weekends, market holidays, and future dates are excluded. The latest
10,000 additions use historical Yahoo Finance daily closes and deliberately
favor profitable round trips using hindsight; they are demo data, not a
representative strategy backtest. Seeded cash and holdings are reconciled from
the trade ledger, and accounts with non-seed trades are protected from reseeding.

The analyst dashboard initially loads its instrument catalog and trade snapshot
without fetching market history for the full catalog. Chart history is requested
separately for the two initially selected instruments, then on demand when an
instrument is selected. Loaded histories are reused until the dashboard reloads;
chart loading and retry errors are displayed independently of dashboard data.
The snapshot includes every trade in its requested date range so volume, KPIs,
and account outcomes are calculated from the full dataset, not the latest page.
The trade-search endpoint remains paginated, and the dashboard table displays
at most 100 matching rows without limiting the chart calculations.

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

## Watchlist Hunter

Turns an investment idea in plain English into a card of rules, finds S&P 500 companies that match it,
and lets a client save matches to a watchlist. The pages are **Hunter** and **Watchlist** in the
client navbar.

All company information comes from the AI model's general knowledge, which ends in May 2026. Nothing
is fetched from market data, filings or news, and results say so. The spec, with the API contract,
the checks the app makes on the model's answers, limits and acceptance criteria, is in
[docs/specs/watchlist-hunter.md](docs/specs/watchlist-hunter.md).

```bash
TOKEN=$(curl -s -X POST http://localhost:8091/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"bob@example.com","password":"123"}' | jq -r .token)
# Turn an idea into an unsaved spec (about 8 seconds)
curl -X POST -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"prompt":"High-margin AI infrastructure companies"}' http://localhost:8091/api/hunter/specs/draft
# Save it with POST /api/hunter/specs, then run it (about 20 seconds; unchanged specs reuse their last run)
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8091/api/hunter/specs/<specId>/runs
```

### Configuration

| Variable or property | Default | Purpose |
| -------------------- | ------- | ------- |
| `OPENAI_API_KEY` | none | Shared with trade analysis; without it Hunter's model calls return 502 |
| `HUNTER_OPENAI_MODEL` | `gpt-6-luna` | Model for drafts and runs |
| `hunter.knowledge-cutoff` | `2026-05-18` | Where the model's knowledge ends; change it with the model |

The S&P 500 list is `src/main/resources/hunter/sp500.csv`, updated by hand.

### Usage log

```bash
docker-compose logs app | grep "hunter "
```

### Testing

```bash
mvn test                                                                                   # offline, no key needed
env $(grep -E '^(OPENAI_API_KEY|HUNTER_)' .env | xargs) mvn test -Dtest=HunterLiveTest           # 2 real calls
env $(grep -E '^(OPENAI_API_KEY|HUNTER_)' .env | xargs) mvn test -Dtest='SpecTranslatorEvalTest,CandidateFinderEvalTest'  # about 100 real calls
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
