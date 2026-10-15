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

Asks OpenAI whether a trade was a good decision, given the trade, its P&L, the account's cash and
holdings, its track record and recent trades. It is an automated review, not financial advice.

### Endpoints

| Method | URL | Returns |
| ------ | --- | ------- |
| `POST` | `/api/trades/{tradeId}/analysis` | The verdict (`GOOD`, `MIXED`, `POOR` or `TOO_EARLY`), a summary, and up to three strengths, concerns and suggestions |
| `GET` | `/api/trades/{tradeId}/analysis/context` | Exactly what the model would be told, without calling OpenAI |

```bash
curl http://localhost:8095/api/trades/c3000000-0000-0000-0000-000000000006/analysis/context
curl -X POST http://localhost:8095/api/trades/c3000000-0000-0000-0000-000000000006/analysis
```

```json
{
  "verdict": "GOOD",
  "summary": "Two or three sentences addressed to the investor ...",
  "strengths": ["..."],
  "concerns": ["..."],
  "suggestions": ["..."]
}
```

| Status | Meaning |
| ------ | ------- |
| 200 | Analysis returned (repeat requests with unchanged data come from a cache and cost nothing) |
| 404 | No trade with that ID |
| 429 | Rate limit reached; the `Retry-After` header says how many seconds to wait |
| 502 | OpenAI is unavailable or misconfigured; the reason is in `docker-compose logs app` |

An analysis takes about 2 to 4 seconds.

### Configuration

Set these in `.env` (see `.env.example`); `docker-compose` passes them to the app.

| Variable | Default | Purpose |
| -------- | ------- | ------- |
| `OPENAI_API_KEY` | none | Required for `POST .../analysis`; without it the app still starts and that endpoint returns 502 |
| `OPENAI_MODEL` | `gpt-4o-mini` | Model used for analyses |
| `ETL_INTERVAL_SECONDS` | `600` | How often the `etl` service refreshes P&L |

The rate limits (20 analyses a minute overall, 5 per client) and cache size are in
`application.properties` under `analysis.*`.

### Where the data comes from

- Trade, cash, holdings and recent trades come from the `paysprint` database at request time.
- P&L comes from `analytics.fact_trades` in `paysprint_analytics`, which the `etl` service
  refreshes every 10 minutes. A trade made since the last run is analysed without P&L, and the
  response's context shows when the P&L was calculated (`outcome.asOf`).
- What is sent to OpenAI: the ticker and company name, prices, quantities, dates, cash, holdings
  and P&L figures. No names, emails, user or account IDs are sent.

### Usage and cost

Every analysis writes one line to the app log, which is the record of how the feature is used:

```text
analysis tradeId=... cache=miss verdict=GOOD model=gpt-4o-mini-2024-07-18 promptTokens=1350 completionTokens=139 latencyMs=3534
```

```bash
docker-compose logs app | grep "analysis tradeId"
```

### Testing

- `mvn test` runs the offline tests; they need no database or API key.
- With a key, `OpenAiTradeAnalyzerLiveTest` sends three mock trades to OpenAI and prints the replies:

  ```bash
  env $(grep -E '^OPENAI_' .env | xargs) mvn test -Dtest=OpenAiTradeAnalyzerLiveTest
  ```

- `TradeAnalysisEvalTest` runs ten frozen trades in `src/test/resources/analysis-eval/` three times
  each and checks every reply against that case's allowed verdicts and banned phrases. Run it after
  changing the prompt or model (`OPENAI_MODEL=...` to try another; about 30 API calls):

  ```bash
  env $(grep -E '^OPENAI_' .env | xargs) mvn test -Dtest=TradeAnalysisEvalTest
  ```

  On 2026-10-15, `gpt-4o-mini` passed 26 of 30 runs and `gpt-5.4-mini` 28 to 29 of 30 with
  noticeably more specific answers. `gpt-4o-mini` stays the default for cost while the feature is
  being proven.

### Resetting inconsistent dev data

Databases seeded before the seeder fix have trades that never moved holdings or cash, so analyses
see a portfolio that contradicts the trade history. To start clean:

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
