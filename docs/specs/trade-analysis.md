# Spec: AI trade analysis

| | |
|---|---|
| Status | Backend merged (#74); UI specified, not built (`analytics/ai_wrapper_UI`) |
| Last updated | 2026-10-20 |

## 1. Goal

A client can ask for an automated review of any trade they have made and get, within a few seconds,
a verdict on whether it was a good decision and why. The review judges the decision (size,
concentration, cash left, trading pattern) as well as the result, and uses only the account's own
data.

It is an automated review, not financial advice, and the UI must say so.

## 2. Scope

**In scope**
- Reviewing one of your own trades on demand, by trade ID, after logging in.
- Showing exactly what the model is told about a trade, without calling the model.
- Refreshing P&L automatically so reviews of recent trades include it.
- Cost controls: a rate limit, a cache, and a usage log.

**Out of scope (follow-ups)**
- **Admin and analyst access:** only a trade's owner can review it.
- **The rest of the API:** other `/api` endpoints, such as `GET /api/trades/account/{accountId}`,
  still need no login and do not check ownership. That is app-wide auth work.
- **Storing reviews:** reviews are not stored, and each request is answered fresh or from the
  in-memory cache.
- **Missing context:** price history, market data, and a note on why the trader made the trade.
- **Replies in the UI language:** answers are English only.
- **Model upgrade:** `gpt-5.4-mini` scored higher in evaluation but costs more (see section 7).

## 3. API contract

Both endpoints require a login token, `Authorization: Bearer <token>` from `POST /api/auth/login`.
They only find trades on the logged-in user's account. Another account's trade gets the same 404 as a
trade that doesn't exist, so responses don't reveal which trade IDs exist.

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
| 401 | No token, or a token that doesn't verify | Empty |
| 404 | No trade with that ID on the caller's account | `ErrorResponse`, code `RESOURCE_NOT_FOUND` |
| 429 | Rate limit reached | `ErrorResponse`, code `TOO_MANY_ANALYSES`; header `Retry-After` in seconds |
| 502 | OpenAI unreachable, refused, misconfigured or returned unusable output | `ErrorResponse`, code `ANALYSIS_UNAVAILABLE`; the cause is logged, never returned |

`TOO_EARLY` means there is no result to judge yet, typically because the P&L has not been calculated.

### `GET /api/trades/{tradeId}/analysis/context`

Returns the exact JSON the model would receive (section 4). It does not call OpenAI and does not
count against the rate limit. Status 200, 401 without a valid token, or 404 for a trade not on the
caller's account.

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
| Rate limit | 20 requests a minute overall and 5 per logged-in user, in-memory per app instance |
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
| B2 | Given an unknown trade ID, when a review or its context is requested, then 404 | `returns404ForAnUnknownTradeOrSomeoneElses`, `TradeAnalysisServiceTest.rejectsAnUnknownTrade` |
| B3 | Given OpenAI fails, when a review is requested, then 502 and the upstream error is not in the response | `returns502WithoutLeakingTheUpstreamError` |
| B4 | Given a user has made 5 requests this minute, when it makes another, then 429 with `Retry-After` and no analysis runs | `returns429WithRetryAfterAndSkipsTheAnalysis`, `AnalysisRateLimiterTest` |
| B5 | Given the context is unchanged, when the same trade is reviewed again, then OpenAI is not called again | `reusesTheAnalysisWhileTheContextIsUnchanged` |
| B6 | Given a new ETL run changed the P&L, when the trade is reviewed, then OpenAI is called again | `analysesAgainWhenANewPnlRunChangesTheContext` |
| B7 | Given a trade the ETL has not processed, when its context is built, then `outcome` and `trackRecord` are null and the review still succeeds | `leavesOutcomeAndTrackRecordEmptyWhenAnalyticsHasNothing` |
| B8 | Given a fully sold buy or a sell with no purchase, when its context is built, then the inapplicable P&L fields are null, not 0 | `givesNoReturnForAFullySoldBuyOrAnUncostedSell`, `buildsContextForACostedSell` |
| B9 | Given any trade, when its context is requested, then OpenAI is not called and the rate limit is not used | `returnsTheContextWithoutCallingOpenAiOrCountingAgainstTheLimit` |
| B10 | Given a trade is placed, when up to 10 minutes pass, then its context shows a non-null `outcome` without a manual pipeline run | Manual check, 2026-10-15: P&L appeared 21 s after the trade with a 30 s interval |
| B11 | Given no OpenAI key, when the app starts, then it runs and reviews return 502 | Manual check, 2026-10-13 |
| B12 | Given the evaluation set, when run against the configured model, then every case passes in every run | `TradeAnalysisEvalTest` (opt-in; 30 of 30 on 2026-10-19) |
| B13 | Given main's live market service, when a subscribed ticker's price changes, then that trade's context shows the new `priceNow` | Manual check, 2026-10-19: NVDA 138.25 to 218.57 after subscribing |
| B14 | Given no token, or a token not signed by this app, when a review or its context is requested, then 401 and nothing runs | `requiresALoginToken`, `rejectsATokenSignedWithAnotherKey` |
| B15 | Given a trade on another account, when a logged-in user requests its review or context, then 404, the same as an unknown trade | `TradeAnalysisServiceTest.treatsAnotherAccountsTradeAsNotFound`, `returns404ForAnUnknownTradeOrSomeoneElses`; manual check 2026-10-19: Bob gets 404 for Alice's trade and 200 for his own |

## 9. UI

### 9.1 User story

As a client on the Trades page, I press **Analyze** on one of my trades and, a few seconds later, see
an automated review of whether it was a good decision and why.

### 9.2 Flow

1. Each row of the trades table has an Analyze button.
2. Pressing it opens a dialog in its loading state and sends two requests in parallel, both with
   `Authorization: Bearer <token>` (the token `AuthService` stored at login):
   - `POST /api/trades/{tradeId}/analysis` for the review;
   - `GET /api/trades/{tradeId}/analysis/context`, read only for `outcome.asOf`, the time the P&L
     was calculated.
3. When the review arrives, the dialog shows it. If the context request failed, the review is still
   shown, without the P&L line.
4. Each trade's result is kept while the page is open. Reopening a reviewed trade shows the stored
   result without a new request, so repeat looks cost no OpenAI call and no rate-limit allowance.
   Leaving or reloading the page clears the stored results, and errors are never stored.

The token is attached to these two requests only, not to every API call. Tokens expire after an
hour, and the backend rejects any request carrying an expired token, even on endpoints that need no
login, so sending it everywhere would break other pages.

### 9.3 States

| State | Dialog shows |
|---|---|
| Loading | A spinner and "Analysing your trade…"; the row's Analyze button is disabled |
| Success | Verdict badge, summary, strengths, concerns and suggestions (an empty list is hidden), the P&L line, the disclaimer |
| 401 | "Your session has expired. Please log in again." |
| 404 | "This trade could not be found." |
| 429 | "Too many analyses. Try again in N seconds.", with N from the `Retry-After` header, or 60 if it is missing |
| 502 | "Analysis is unavailable right now. Please try again later." |
| Network error or any other status | "Something went wrong. Please try again." |

### 9.4 Content

- **Title:** "Trade analysis", with a subtitle naming the trade: ticker, side, quantity and date,
  e.g. "NVDA · Sell · 8 · Oct 19, 2026".
- **Verdict badge:**

  | Verdict | Label | Colour |
  |---|---|---|
  | `GOOD` | Good decision | Green |
  | `MIXED` | Mixed | Amber |
  | `POOR` | Poor decision | Red |
  | `TOO_EARLY` | Too early to tell | Grey |

- **Lists:** headed "Strengths", "Concerns" and "Suggestions", in that order.
- **P&L line:** "P&L as of {date and time}" when the context has an `outcome`, otherwise "P&L not
  calculated yet. This review does not include profit or loss."
- **Disclaimer**, shown with every result: "Automated review, not financial advice."

### 9.5 Language

Every label, message and button text is translated into the app's 10 languages through
`TranslationService`. The review itself (summary and lists) is written by the model in English;
replies in the UI language are already listed as out of scope.

### 9.6 Accessibility

- The dialog has `role="dialog"`, `aria-modal="true"` and `aria-labelledby` pointing at its title.
- On open, focus moves to the dialog's close button.
- Esc, the close button or a click on the backdrop closes it, and focus returns to the Analyze button
  that opened it.
- The loading and result area is an `aria-live="polite"` region, so screen readers announce the
  result.
- Each Analyze button's accessible name names its trade, e.g. "Analyze NVDA sell from Oct 19, 2026".

### 9.7 Out of scope

- An app-wide auth interceptor and token refresh. Today an expired token only affects this feature,
  through the 401 message.
- The unused `ApiInterceptor`, which is never applied because `provideHttpClient()` is called
  without `withInterceptorsFromDi()`.
- Analysis anywhere other than the Trades page, such as the Portfolio page or the trade confirmation.
- Showing the full context to the user.

### 9.8 Acceptance criteria (UI)

| # | Given / When / Then | Covered by |
|---|---|---|
| U1 | Given a logged-in client on the Trades page, when the trades load, then each row has an Analyze button whose accessible name names the trade | `client-trades.component.spec.ts` |
| U2 | Given a row, when its Analyze button is pressed, then the dialog opens in the loading state and that button is disabled until the request finishes | `client-trades.component.spec.ts`, `trade-analysis-dialog.component.spec.ts` |
| U3 | Given an analysis request, when it is sent, then both the review and the context requests carry `Authorization: Bearer <stored token>` | `trade-analysis.service.spec.ts` |
| U4 | Given the review succeeds, when the dialog shows it, then it has the verdict badge with its translated label and colour, the summary, the non-empty lists, and the disclaimer | `trade-analysis-dialog.component.spec.ts`, manual check |
| U5 | Given the context has an `outcome`, when the review is shown, then the dialog says "P&L as of" with that time | `trade-analysis.service.spec.ts`, `trade-analysis-dialog.component.spec.ts` |
| U6 | Given the context's `outcome` is null, or the context request fails, when the review is shown, then the dialog says P&L is not calculated yet, or shows no P&L line if the context failed | `trade-analysis.service.spec.ts`, `trade-analysis-dialog.component.spec.ts`, manual check |
| U7 | Given a trade was reviewed earlier on this page, when its Analyze button is pressed again, then the stored result is shown and no request is sent | `client-trades.component.spec.ts`, manual check |
| U8 | Given the review returns 401, when the dialog updates, then it shows the session-expired message | `trade-analysis.service.spec.ts`, `trade-analysis-dialog.component.spec.ts`, manual check |
| U9 | Given the review returns 429, when the dialog updates, then it shows the rate-limit message with the seconds from `Retry-After`, or 60 without the header | `trade-analysis.service.spec.ts`, `trade-analysis-dialog.component.spec.ts`, manual check |
| U10 | Given the review returns 404, 502, or fails without a response, when the dialog updates, then it shows the matching message from section 9.3, and pressing Analyze again sends a new request | `trade-analysis.service.spec.ts`, `trade-analysis-dialog.component.spec.ts`, `client-trades.component.spec.ts` |
| U11 | Given the dialog is open, when Esc, the close button or the backdrop is used, then it closes and focus returns to the Analyze button that opened it; on open, focus is on the close button | `trade-analysis-dialog.component.spec.ts`, manual check |
| U12 | Given any of the 10 languages is selected, when the dialog is shown, then its labels and messages are in that language | `translation.service` key check in `trade-analysis-dialog.component.spec.ts`, manual check |
