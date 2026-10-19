# Spec: AI trade analysis

| | |
|---|---|
| Status | Backend implemented (`analytics/wrapper`); UI not started |
| Last updated | 2026-10-19 |

## 1. Goal

A client can ask for an automated review of any trade they have made and get, within a few seconds,
a verdict on whether it was a good decision and why. The review judges the decision (size,
concentration, cash left, trading pattern) as well as the result, and uses only the account's own
data.

It is an automated review, not financial advice, and the UI must say so.

## 2. Scope

**In scope**
- Reviewing any stored trade on demand, by trade ID.
- Showing exactly what the model is told about a trade, without calling the model.
- Refreshing P&L automatically so reviews of recent trades include it.
- Cost controls: a rate limit, a cache, and a usage log.

**Out of scope (follow-ups)**
- **Trade ownership check:** a caller can review any trade ID. This is blocked until login works end
  to end (the frontend sends no token).
- **Storing reviews:** reviews are not stored, and each request is answered fresh or from the
  in-memory cache.
- **Missing context:** price history, market data, and a note on why the trader made the trade.
- **Replies in the UI language:** answers are English only.
- **Model upgrade:** `gpt-5.4-mini` scored higher in evaluation but costs more (see section 7).

## 3. API contract

### `POST /api/trades/{tradeId}/analysis`

No request body. A successful response:

```json
{
  "verdict": "GOOD | MIXED | POOR | TOO_EARLY",
  "summary": "Two or three sentences addressed to the investor as \"you\".",
  "strengths": ["at most 3 items"],
  "concerns": ["at most 3 items"],
  "suggestions": ["at most 3 items"]
}
```

| Status | When | Body |
|---|---|---|
| 200 | Analysis returned, possibly from the cache | As above |
| 404 | No trade with that ID | `ErrorResponse`, code `RESOURCE_NOT_FOUND` |
| 429 | Rate limit reached | `ErrorResponse`, code `TOO_MANY_ANALYSES`; header `Retry-After` in seconds |
| 502 | OpenAI unreachable, refused, misconfigured or returned unusable output | `ErrorResponse`, code `ANALYSIS_UNAVAILABLE`; the cause is logged, never returned |

`TOO_EARLY` means there is no result to judge yet, typically because the P&L has not been calculated.

### `GET /api/trades/{tradeId}/analysis/context`

Returns the exact JSON the model would receive (section 4). It does not call OpenAI and does not
count against the rate limit. Status 200, or 404 for an unknown trade.

## 4. Context sent to the model

The context is built at request time from two databases:

| Block | Source | Contents |
|---|---|---|
| `trade` | `paysprint.trades` + `instruments` | ticker, name, exchange, side, quantity, executionPrice, tradeValue, executedAt, daysAgo |
| `outcome` | `paysprint_analytics.analytics.fact_trades` | entryPrice, exitPrice, realizedPnl, unrealizedPnl, returnPct, pnlStatus, asOf; the whole block is `null` until the ETL has processed the trade |
| `priceNow` | `instruments.current_price` (live) | currentPrice, movePctSinceExecution |
| `portfolio` | `accounts.balance` + `holdings` × current price (live) | currency, cash, cashPct, totalValue, tradeValuePctOfTotal, holdings with marketValue and weightPct |
| `trackRecord` | totals over the account's rows in `fact_trades` | totalRealizedPnl, totalUnrealizedPnl, winRatePct; `null` if there is no history |
| `recentTrades` | `paysprint.trades` | the account's 10 latest trades |

Rules:
- **Null instead of zero.** Fields that do not apply, or whose value is unknown, are `null`, never
  0. That covers realized P&L on buys and on sells with no recorded purchase, and unrealized P&L on
  sells and fully sold buys. The model reads 0 as break-even.
- **Today's portfolio.** It may be long after the trade, and the prompt says so.
- **Freshness.** P&L is at most about 10 minutes old (section 5). The figures in `priceNow` and
  `portfolio` are live.
- **Never sent:** names, emails, user, account, trade or instrument IDs, and anything from `user_pii`.

## 5. Data flow

1. A trade placed through `POST /api/trades` updates `trades`, `accounts.balance` and `holdings` in one transaction.
2. The `etl` service in docker-compose runs the analytics pipeline every `ETL_INTERVAL_SECONDS`
   (default 600). Each run is a full refresh of `analytics.fact_trades` with first-in, first-out P&L.
   An advisory lock keeps it safe alongside manual runs.
3. A review request builds the context (section 4), checks the cache, calls OpenAI if needed and
   returns the result.

If the analytics database is missing or being refreshed, reviews still work, without `outcome` and `trackRecord`.

### Known limitations

- **Live prices only for subscribed tickers.** `current_price` comes from the market service only
  for tickers someone has subscribed to (`POST /api/market/subscriptions/{ticker}`, done by the
  market pages); other prices stay at their last value.
- **Seeded prices are not real.** `seed_trades.py` and `data.sql` use made-up prices (for example
  NVDA around 138), while live quotes are real (NVDA was 218.57 on 2026-10-19). Once a ticker has
  live quotes, `priceNow` and unrealized P&L on seeded trades show artificial moves of about 50%, and
  reviews judge seeded trades against them. Trades placed through the app execute at the live price
  and are unaffected. Follow-up: seed at prices near the current quote.

## 6. Non-functional requirements

| Requirement | Value |
|---|---|
| Latency | 2–4 s for an OpenAI call; about 10 ms from the cache |
| Rate limit | 20 requests a minute overall and 5 per client, in-memory per app instance; client = `X-Real-IP` set by nginx, else the remote address |
| Cache | Keyed by the full context, so any change to what the model would see is a miss; most recent 500 entries |
| Usage log | One line per request: tradeId, cache hit or miss, verdict, model, prompt and completion tokens, latency |
| Configuration | `OPENAI_API_KEY` (required for reviews), `OPENAI_MODEL` (default `gpt-4o-mini`), `ETL_INTERVAL_SECONDS`, `analysis.*` in `application.properties` |
| Failure isolation | The app starts without an OpenAI key and without the analytics database |

## 7. Answer quality

The prompt in `OpenAiTradeAnalyzer.SYSTEM_PROMPT` limits the model to the figures in the context. It
forbids talk of market conditions, trends, momentum, news, volatility, sectors, timing and stop-losses,
and it forbids comparisons with any price not given.

`TradeAnalysisEvalTest` checks this against ten frozen cases in `src/test/resources/analysis-eval/`
(six real trades and four edge cases). It runs each case three times. A run passes if the verdict is
allowed for the case and no banned phrase appears.

| Date | Model | Runs passing |
|---|---|---|
| 2026-10-15 | `gpt-4o-mini` (before the prompt fixes) | 17 of 30 |
| 2026-10-15 | `gpt-4o-mini` | 26 of 30 |
| 2026-10-15 | `gpt-5.4-mini` | 28 to 29 of 30, with more specific answers |
| 2026-10-19 | `gpt-4o-mini` (after the `recentTrades` fix) | 30 of 30 |

Results vary from run to run, so compare models over several runs.

## 8. Acceptance criteria (backend)

| # | Given / When / Then | Covered by |
|---|---|---|
| B1 | Given a stored trade, when a review is requested, then 200 with a verdict, a summary and the three lists | `TradeAnalysisEndpointTest.returnsTheAnalysis` |
| B2 | Given an unknown trade ID, when a review or its context is requested, then 404 | `returns404ForAnUnknownTrade`, `TradeAnalysisServiceTest.rejectsAnUnknownTrade` |
| B3 | Given OpenAI fails, when a review is requested, then 502 and the upstream error is not in the response | `returns502WithoutLeakingTheUpstreamError` |
| B4 | Given a client has made 5 requests this minute, when it makes another, then 429 with `Retry-After` and no analysis runs | `returns429WithRetryAfterAndSkipsTheAnalysis`, `AnalysisRateLimiterTest` |
| B5 | Given the context is unchanged, when the same trade is reviewed again, then OpenAI is not called again | `reusesTheAnalysisWhileTheContextIsUnchanged` |
| B6 | Given a new ETL run changed the P&L, when the trade is reviewed, then OpenAI is called again | `analysesAgainWhenANewPnlRunChangesTheContext` |
| B7 | Given a trade the ETL has not processed, when its context is built, then `outcome` and `trackRecord` are null and the review still succeeds | `leavesOutcomeAndTrackRecordEmptyWhenAnalyticsHasNothing` |
| B8 | Given a fully sold buy or a sell with no purchase, when its context is built, then the inapplicable P&L fields are null, not 0 | `givesNoReturnForAFullySoldBuyOrAnUncostedSell`, `buildsContextForACostedSell` |
| B9 | Given any trade, when its context is requested, then OpenAI is not called and the rate limit is not used | `returnsTheContextWithoutCallingOpenAiOrCountingAgainstTheLimit` |
| B10 | Given a trade is placed, when up to 10 minutes pass, then its context shows a non-null `outcome` without a manual pipeline run | Manual check, 2026-10-15: P&L appeared 21 s after the trade with a 30 s interval |
| B11 | Given no OpenAI key, when the app starts, then it runs and reviews return 502 | Manual check, 2026-10-13 |
| B12 | Given the evaluation set, when run against the configured model, then every case passes in every run | `TradeAnalysisEvalTest` (opt-in; 30 of 30 on 2026-10-19) |
| B13 | Given main's live market service, when a subscribed ticker's price changes, then that trade's context shows the new `priceNow` | Manual check, 2026-10-19: NVDA 138.25 to 218.57 after subscribing |

## 9. UI

To be specified before UI work starts, on the `frontend/trade-analysis` branch. It must cover:
- **States:** loading, success, P&L not yet calculated, 404, 429, 502 and network error.
- **Content:** the "not financial advice" disclaimer and "P&L as of {outcome.asOf}".
- **i18n:** translations for all 10 UI languages.
- **Accessibility.**
- **Acceptance criteria:** U1 onwards, each mapped to a test.
