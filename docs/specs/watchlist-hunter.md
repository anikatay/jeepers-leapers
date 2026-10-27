# Spec: Watchlist Hunter

| | |
|---|---|
| Status | Built 2026-10-23, not yet reviewed or merged; manual checks open (section 11) |
| Last updated | 2026-10-23 |

## 1. Goal

A client describes an investment idea in plain English, for example "Find high-margin AI
infrastructure companies with improving cash flow", and gets back a short list of S&P 500 companies
that match it. Each match has a score and up to three reasons.

The idea is turned into a **Hunter Spec**, a card of rules the client can read, edit, save and run
again. The client can add any match to a simple watchlist and view that watchlist later.

All company information comes from the AI model's general knowledge. No market data, filings or news
are fetched. Results are therefore **educational candidate matches based on the AI's general
knowledge up to May 2026**. They are not checked against any source and may be out of date. They are
never buy, sell or hold recommendations. The UI says this on every result (section 8.4).

## 2. Scope

**In scope**
- Turning a prompt into an editable Hunter Spec, then saving, editing, deleting and running it.
- Finding up to 15 S&P 500 companies that the model judges to match. Each comes with the model's
  estimates for the numeric rules, a verdict for each qualitative check, a 0–100% score and up to
  three reasons.
- A watchlist: add a match, view the list, remove an item.
- Cost controls: rate limits, reuse of unchanged runs, and a usage log.

**Out of scope (decided 2026-10-23)**
- **External data.** No prices, SEC filings, news or transcripts. The app's only outbound call is to
  the model.
- **Recent events and price moves.** The model's knowledge ends on May 18, 2026, so phrases like
  "pulled back this month" or "after last week's earnings" can't be checked. They become "Not used"
  notes on the card.
- **Citations.** The model has no documents in front of it, so results quote nothing and name no
  source. This drops rule 3 of the original brief, which tied every reason to a quote.
- **Checking the model's figures.** Estimates are shown as estimates, with the period they refer to.

**Follow-ups**
- Alerts and scheduled re-runs.
- Watchlist extras: notes, sorting, prices, multiple lists, and adding tickers from other pages.
- Universes other than the S&P 500.
- Admin and analyst access; specs and watchlists belong to the user who made them.
- Replies in the UI language; model-written text is in English, as in trade analysis.
- Moving trade analysis to the newer model, which needs its evaluation re-run.

## 3. Source: the model

| Setting | Value | Property |
|---|---|---|
| Model | `gpt-6-luna` | `HUNTER_OPENAI_MODEL`, separate from trade analysis's `OPENAI_MODEL` |
| Knowledge cutoff | May 18, 2026 | `hunter.knowledge-cutoff=2026-05-18` |
| Reasoning effort | `low` | `hunter.reasoning-effort` |
| Output budget | 8,000 tokens, which includes hidden reasoning tokens | `hunter.max-output-tokens` |
| Endpoint | Chat Completions with a strict JSON schema, as in trade analysis | |
| Price | $0.10 per 1M input tokens, $0.50 per 1M output tokens | |

These figures come from OpenAI's model pages, checked on 2026-10-23. `gpt-6-luna` was chosen over the
app's `gpt-4o-mini` because its knowledge is two and a half years newer (May 2026 against October 2023)
and it costs less. Its page does not confirm support for `temperature`, so that is not set.

`hunter.knowledge-cutoff` is sent to the model, so it knows where its knowledge ends, and it is shown
on every result. Change it whenever `HUNTER_OPENAI_MODEL` changes.

**Universe.** The S&P 500 list is a static file in the app, `src/main/resources/hunter/sp500.csv`, with
columns `ticker, name, gics_sector, as_of`.
- **Where it comes from:** it was made on 2026-10-23 from Wikipedia's "List of S&P 500 companies"
  table (503 tickers, 11 sectors). Tickers use Yahoo's form (`BRK-B`), as the rest of the app does.
- **Updates:** the file is updated by hand, and the app never downloads it.
- **What the app uses it for:**
  - dropping any ticker the model returns that is not in the index, because it was invented or has
    left the index since the model's cutoff;
  - showing each match's name and sector from the list rather than from the model.

The list may include companies added after May 2026 that the model knows little about. The prompt
tells the model to skip companies it does not know well.

## 4. Hunter Spec

### 4.1 Shape

The UI and API exchange this JSON, and the Java records and
`frontend/src/app/core/models/hunter.model.ts` mirror it. Fields that may have no value are `null`,
never omitted and never 0. Percentages are in percent units (12.5 means 12.5%).

```ts
type MetricId =
  | 'marketCapUsdB' | 'revenueGrowthPct' | 'grossMarginPct' | 'operatingMarginPct'
  | 'fcfMarginPct' | 'peRatio' | 'priceToSales';

type GicsSector =
  | 'Communication Services' | 'Consumer Discretionary' | 'Consumer Staples' | 'Energy'
  | 'Financials' | 'Health Care' | 'Industrials' | 'Information Technology' | 'Materials'
  | 'Real Estate' | 'Utilities';

interface HunterSpec {
  specVersion: 1;
  id: string | null;                  // null until saved
  name: string;                       // suggested by the translator, editable, max 80 chars
  prompt: string;                     // the user's words, max 500 chars
  universe: 'SP500';
  quantRules: QuantRule[];            // filters: every enabled rule must pass; max 8
  sectorRule: SectorRule | null;      // filter
  criteria: Criterion[];              // qualitative checks, scored; max 5
  unsupported: UnsupportedClause[];   // parts of the prompt that became no rule
  candidateLimit: number;             // 1–15, default 10
}

interface RuleBase {
  id: string;                  // unique within the spec: "q1", "sector", "s1"
  enabled: boolean;            // a disabled rule is kept on the card but ignored
  sourceText: string | null;   // the phrase of the prompt it came from; null if the user added it
}

interface QuantRule extends RuleBase {
  metric: MetricId;
  op: 'gt' | 'gte' | 'lt' | 'lte' | 'between';
  value: number;
  value2: number | null;       // upper bound, only for 'between'
}

interface SectorRule extends RuleBase {
  op: 'in' | 'notIn';
  values: GicsSector[];
}

interface Criterion extends RuleBase {
  statement: string;           // one claim about the company, max 200 chars
}

interface UnsupportedClause {
  sourceText: string;
  reason: 'NO_RECENT_DATA' | 'NO_DATA' | 'AMBIGUOUS' | 'ADVICE_REQUEST' | 'OUT_OF_UNIVERSE';
  message: string;             // shown on the card
}
```

Each user can save at most 20 specs.

### 4.2 Metric catalog

`GET /api/hunter/catalog` serves this list. It is the only source for the translator prompt, the
validator and the UI labels. Every value is the model's estimate for the latest period it knows.

| Metric | Label | Unit | Definition |
|---|---|---|---|
| `marketCapUsdB` | Market cap | $ bn | Share price × shares outstanding |
| `revenueGrowthPct` | Revenue growth | % | Latest fiscal year against the year before |
| `grossMarginPct` | Gross margin | % | Gross profit ÷ revenue |
| `operatingMarginPct` | Operating margin | % | Operating income ÷ revenue |
| `fcfMarginPct` | Free cash flow margin | % | (Operating cash flow − capital expenditure) ÷ revenue |
| `peRatio` | P/E | × | Share price ÷ earnings per share |
| `priceToSales` | Price/sales | × | Market cap ÷ revenue |

### 4.3 Translating a prompt

`SpecTranslator` sends the prompt, the catalog and the knowledge cutoff to the model and asks for a
spec in a strict JSON schema. The server fills in `specVersion`, `id`, `universe` and
`candidateLimit`, then validates the spec like any other (section 6.2). The prompt tells the model to:

- Use only the metrics and sectors in the catalog. Anything the catalog cannot express and general
  knowledge cannot judge, such as "insider buying", becomes an `unsupported` clause with reason
  `NO_DATA`.
- Use these defaults for vague words, so the same words always give the same rule. The card shows
  them and the user can change them:

  | Words | Rule |
  |---|---|
  | high-margin | `grossMarginPct gte 50` |
  | profitable | `operatingMarginPct gt 0` |
  | fast-growing, high-growth | `revenueGrowthPct gte 20` |
  | cash-generative | `fcfMarginPct gte 15` |
  | cheap, low P/E | `peRatio lte 15` |
  | mega-cap | `marketCapUsdB gte 200` |

- Turn a word into the sector rule only when it clearly names a GICS sector ("tech companies" →
  Information Technology). Narrower industries and themes ("semiconductors", "AI infrastructure",
  "founder-led", "subscription software") and trends ("improving cash flow") become qualitative
  checks.
- Write each check as one statement about what the company is or does, as generally known, never about
  recent events.
- Turn time-based phrases ("this month", "this week", "recently", "pulled back", "after earnings",
  "Q3", "this year's guidance") into a `NO_RECENT_DATA` clause with the message "The AI's knowledge
  ends in May 2026, so recent price moves and events can't be checked." Build no rule from them.
- Turn requests for advice ("what should I buy", "stocks that will double") into an
  `ADVICE_REQUEST` clause and build no rule from them.

Example: "Find high-margin AI infrastructure stocks that pulled back more than 10% this month".

```
q1      grossMarginPct gte 50          sourceText "high-margin"
s1      "Sells products or services used to build or run AI data centers"
                                       sourceText "AI infrastructure"
Not used: NO_RECENT_DATA               sourceText "pulled back more than 10% this month"
```

## 5. Running a spec

### 5.1 Steps

1. **Validate** the spec (section 6.2).
2. **Reuse.** A run stores the hash of its spec's enabled rules and checks, `candidateLimit`, the model
   and the prompt version. If the latest saved run has the same hash, return it with no model call.
   The model's knowledge does not change, so a fresh call would only add cost and variation.
3. **Ask** the model, in one call, for up to `candidateLimit + 5` S&P 500 companies that match. The
   extra five cover companies removed by the checks in step 4. The call sends:
   - the enabled rules and checks;
   - the knowledge cutoff;
   - the instructions in section 5.2.

   It does not send the full company list, which would add tokens without telling the model anything
   about the companies.
4. **Check** in code (section 5.3).
5. **Score and order** the remaining candidates (section 5.4), keep the first `candidateLimit`, save
   the run and log usage.

If the call fails or returns unusable output, the run returns 502 and nothing is saved.

### 5.2 What the model returns

```json
{ "candidates": [ {
    "ticker": "NVDA",
    "estimates": [ { "ruleId": "q1", "value": 75.0, "period": "FY2026" } ],
    "criteria": [ { "criterionId": "s1", "verdict": "MET | PARTIAL | NOT_MET | UNKNOWN", "reason": "One sentence, or null" } ],
    "whyItMatched": [ "At most 3 short sentences" ]
} ] }
```

`value` and `period` may be null. The prompt tells the model to:
- **Choose carefully:**
  - Choose only S&P 500 companies it knows well, and skip any it is unsure about.
  - For each numeric rule, give its best estimate for the latest period it knows and name that period,
    or null if it doesn't know.
  - Never choose a company that it estimates fails a rule.
- **Judge honestly:** judge each check from general knowledge, and answer `UNKNOWN` when unsure.
- **Invent no sources:**
  - Never quote anything.
  - Never name a document, filing, earnings call, article or date as a source.
  - Never mention events after the knowledge cutoff.
- **Give no advice:** describe the company, never whether the stock is a good investment.

### 5.3 Checks in code

1. **Universe.** Drop tickers that are not in `sp500.csv`, and duplicates.
2. **Sector.** If the sector rule is enabled, drop candidates whose sector in the list fails it.
3. **Numeric rules.** Code compares the model's estimate with each enabled rule's threshold, so the
   model cannot call 45% a pass for "at least 50%". Drop the candidate if any estimate fails or is
   null. Unknown never passes.
4. **Missing verdicts.** A check the model left out counts as `UNKNOWN`.
5. **`SourceGuard`** stops the model presenting invented sources. It removes any reason or bullet
   that contains double quotation marks or any of: "10-K", "10-Q", "8-K", "filing", "transcript",
   "earnings call", "press release", "according to", "source:". A check whose reason is removed keeps
   its verdict with a null reason.
6. **`AdviceGuard`** removes any reason or bullet with advice language: "should buy", "consider
   selling", "buy rating", "price target", "undervalued", "overvalued", "upside", "bargain",
   "attractive entry", "good investment", "recommend". A flagged spec name from the translator is
   replaced with "My hunter".

Both guard lists are tuned against the evaluation sets (section 9), and every removal is counted in
the usage log.

### 5.4 Score, reasons and order

- **Thesis match score.** Each enabled check scores 1 for `MET`, 0.5 for `PARTIAL` and 0 for `NOT_MET`
  or `UNKNOWN`. The score is `round(100 × average)`, so 2 met and 1 partial gives 83%. Code computes it
  from the verdicts; the model never produces a number. Numeric and sector rules are filters, not
  scored, because every match has passed them. The score is `null` when the spec has no enabled
  checks.
- **Why it matched.** Up to 3 bullets: the model's bullets that survived the checks. Any remaining
  slots are filled from the estimates with a fixed template, for example "Gross margin estimated at
  about 75% (FY2026)."
- **Order.** Score from high to low, with nulls last, then the model's own order. Matches scoring 0 are
  collapsed in the UI under "Passed the filters but met no qualitative checks".

### 5.5 Storage

All tables are in `paysprint` (`schema.sql`). The feature does not use the analytics database.

| Table | Contents |
|---|---|
| `hunter_specs` | spec_id (PK), user_id (FK users), name, prompt, spec (JSONB), created_at, updated_at |
| `hunter_runs` | run_id (PK), spec_id (FK, cascade delete), spec_hash, ran_at, result (JSONB), model, match_count, prompt_tokens, completion_tokens, latency_ms; the latest 10 runs per spec are kept |
| `watchlist_items` | user_id (FK users), ticker, added_at, source_spec_id (FK hunter_specs, set null on delete); PK (user_id, ticker) |

## 6. API contract

Every endpoint requires `Authorization: Bearer <token>`. Specs, runs and watchlist items are found
only among the caller's own, and another user's spec gets the same 404 as one that doesn't exist.
Errors use the existing `ErrorResponse`.

| Endpoint | Purpose | Calls the model | Rate-limited |
|---|---|---|---|
| `GET /api/hunter/catalog` | Metrics, sectors, model, knowledge cutoff, universe `as_of` | No | No |
| `POST /api/hunter/specs/draft` | `{ "prompt": "…" }` → unsaved `HunterSpec` | Yes | Yes |
| `GET /api/hunter/specs` | The caller's specs, each with `lastRunAt` and `lastMatchCount` | No | No |
| `POST /api/hunter/specs` | Save a new spec → 201 with `id` | No | No |
| `GET`, `PUT`, `DELETE /api/hunter/specs/{id}` | Read, replace, delete (runs are deleted too) | No | No |
| `POST /api/hunter/specs/{id}/runs` | Run the saved spec → `HunterRun` | Unless the spec is unchanged | Yes |
| `GET /api/hunter/specs/{id}/runs/latest` | The last saved run, or 404 if never run | No | No |
| `GET /api/watchlist` | The caller's watchlist (section 7) | No | No |
| `PUT /api/watchlist/{ticker}` | Add, `{ "sourceSpecId": "…" \| null }` | No | No |
| `DELETE /api/watchlist/{ticker}` | Remove | No | No |

### 6.1 Run result

```ts
interface HunterRun {
  runId: string;
  specId: string;
  ranAt: string;
  source: { model: string; knowledgeCutoff: string };   // "gpt-6-luna", "2026-05-18"
  matches: StockMatchResult[];
  droppedCount: number;            // candidates removed by the checks in section 5.3
  disclaimer: string;              // section 8.4
  reused: boolean;                 // true when an unchanged spec returned its saved run (section 5.1)
}

interface StockMatchResult {
  ticker: string;
  companyName: string;             // from the universe list
  gicsSector: GicsSector;          // from the universe list
  matchScore: number | null;
  whyItMatched: string[];          // at most 3
  criteria: { criterionId: string; verdict: 'MET' | 'PARTIAL' | 'NOT_MET' | 'UNKNOWN'; reason: string | null }[];
  estimates: { ruleId: string; metric: MetricId; value: number; period: string | null }[];  // one per enabled numeric rule
  onWatchlist: boolean;
}
```

### 6.2 Validation and status codes

A spec is valid when:
- every metric and sector is in the catalog;
- each operator suits its rule, `value2` is set only for `between` and is greater than `value`;
- all limits in section 4.1 hold;
- rule IDs are unique;
- every check has a statement.

Running a spec also needs at least one enabled filter or check; otherwise the run is a 400 with the
field `rules`. A spec can be saved without any.

| Status | When | Code |
|---|---|---|
| 200 / 201 | Success; a run that keeps no candidates is still 200, with empty `matches` | |
| 204 | Spec deleted, watchlist item removed (also when it was not there) | |
| 400 | Invalid spec, or a prompt that is empty or over 500 characters | `VALIDATION_ERROR`, with a field error for each problem |
| 401 | No token or a token that doesn't verify | Empty body |
| 404 | Spec not found among the caller's, ticker not in the universe, no run yet | `RESOURCE_NOT_FOUND` |
| 409 | 20 specs or 100 watchlist items already | `LIMIT_REACHED` (new) |
| 429 | Rate limit reached | `TOO_MANY_ANALYSES`, with header `Retry-After` |
| 502 | The model was unreachable, refused, misconfigured or returned unusable output | `ANALYSIS_UNAVAILABLE`; the cause is logged, never returned |

## 7. Watchlist

Deliberately small: a list of tickers per user.

- **Add.** `PUT /api/watchlist/{ticker}` adds the ticker, which must be in the universe, and returns
  the item: 201 when added and 200 when the ticker was already there, so adding twice never makes a
  second entry. `BRK.B` and `brk-b` both mean `BRK-B`.
  `sourceSpecId`, if given, must be one of the caller's specs and records which hunter found it.
- **View.** `GET /api/watchlist` returns the items, newest first. `companyName` and `gicsSector` come
  from the universe list and are null if the ticker has since left it.

  ```ts
  interface WatchlistItem {
    ticker: string; companyName: string | null; gicsSector: GicsSector | null;
    addedAt: string; sourceSpec: { id: string; name: string } | null;
  }
  ```

- **Remove.** `DELETE /api/watchlist/{ticker}` returns 204, also when the ticker was not on the list.
- **Limits.** At most 100 items. Deleting a spec keeps the items it found, and their `sourceSpec`
  becomes null. The watchlist never calls the model.

## 8. UI

### 8.1 User stories

- As a client, I type an idea, see it as a card of rules, adjust the rules, and get matching S&P 500
  companies with reasons.
- As a client, I add a match to my watchlist and later open my watchlist to see what I saved.

### 8.2 Pages

The navbar gets two items, **Hunter** (`/client/hunter`) and **Watchlist** (`/client/watchlist`). The
Hunter page also has a "View watchlist (n)" link.

**Hunter page**
1. **Prompt bar.** A text area (500 characters), three example prompts as buttons, and a **Build
   hunter** button.
2. **Saved hunters.** The caller's specs with their last run time and match count. Selecting one opens
   its card and its latest run.
3. **Hunter card.** An editable name, then three groups:
   - **Filters:**
     - each numeric rule as a sentence, for example "Gross margin is at least [50] %", with an on/off
       switch, an operator menu, value fields and a remove button;
     - the sector rule as removable sector chips with its own switch;
     - **Add filter**, which opens a menu of the catalog.
   - **Qualitative checks:** each statement (editable), with an on/off switch and a remove button.
     **Add check** adds an empty one.
   - **Not used:** each `unsupported` clause with its message.

   **Run hunter** saves the spec (`POST` or `PUT`) and then runs it.
4. **Results.**
   - **Header:** "Educational candidate matches", a badge reading "AI knowledge · up to May 2026 · not
     verified", and the disclaimer.
   - **Match card:**
     - ticker, company name and sector;
     - the score badge, for example "83% thesis match", or "Matches all filters" when there is no
       score;
     - **Why it matched**, with up to 3 bullets;
     - **Score breakdown** (collapsed), with each check's verdict and reason;
     - the estimates, each labelled "AI estimate" with its period, e.g. "Gross margin ≈ 75% · FY2026 ·
       AI estimate";
     - an **Add to watchlist** button that becomes "On watchlist ✓" and removes the item when pressed
       again.

**Watchlist page.**
- **Table:** ticker, company, sector, date added, "Found by" (the hunter's name, which opens it), and
  a **Remove** button.
- **Disclaimer:** shown above the table.
- **Empty state:** "Your watchlist is empty. Add stocks from your Hunter results." with a link to the
  Hunter page.

### 8.3 States

| State | Shows |
|---|---|
| Drafting | Spinner and "Reading your idea…"; Build hunter disabled |
| Running | Spinner and "Finding matching companies…"; Run hunter disabled |
| No matches | "The AI found no S&P 500 companies matching all your rules. Try loosening a filter." |
| Unchanged spec | "Same rules as your run on {date}. Showing those results." |
| 400 | The invalid fields are marked on the card |
| 401 | "Your session has expired. Please log in again." |
| 404 | "This hunter could not be found." |
| 409 | "You have reached the limit of 20 hunters." or "Your watchlist is full (100 stocks)." |
| 429 | "Too many requests. Try again in 41 seconds." (from `Retry-After`, 60 seconds without it) |
| 502 | "The AI is unavailable right now. Please try again later." |
| Network error or other status | "Something went wrong. Please try again." |

### 8.4 Wording

- **Disclaimer**, with every result and on the watchlist page: "These companies match the criteria you
  set, according to the AI's general knowledge up to May 2026. Nothing here has been checked against
  filings or market data, and figures may be out of date. This is for research and education, not a
  recommendation to buy, sell or hold."
- **Never** "recommended", "top picks", "best stocks", "opportunity" or "buy". Use "match",
  "candidate" and "your criteria".
- **Never present anything as sourced or current:** no "as of today", no "latest results". Every
  figure is labelled "AI estimate" with its period.
- The score badge always shows its text, so colour is never the only signal.

### 8.5 Language

Every label, message and button is translated into every language in `LANGUAGES` (10 at present) through
`TranslationService`. Model-written text (spec names, statements, reasons, bullets) is in English.

- The strings live in `core/i18n/hunter.translations.ts` and are registered when the lazy Hunter pages
  load, so they add nothing to the initial bundle.
- The non-English text was machine-written and needs a check by native speakers.
- GICS sector names, tickers and company names stay in English.
- The navbar labels are the keys `nav.hunter` and `nav.watchlist`, kept in the main table in
  `translation.service.ts` rather than with Hunter's lazy strings, because the navbar is on every page.
  "Hunter" is a name and stays in Latin script in every language.

### 8.6 Accessibility

- Rule switches are `role="switch"` with `aria-checked`, and each one's name names its rule.
- The run state and the results are `aria-live="polite"` regions.
- The watchlist button uses `aria-pressed`, and its name includes the ticker, e.g. "Add NVDA to
  watchlist".
- Focus and dialog patterns follow the trade analysis dialog.

## 9. Answer quality

Two opt-in evaluations follow the pattern of `TradeAnalysisEvalTest`. Each runs every case 3 times.

**`SpecTranslatorEvalTest`**, 20 frozen prompts in `src/test/resources/hunter-eval/translator-cases.json`. A run
passes if:
- the expected metrics appear with the expected direction, and sectors are mapped as expected;
- time-based phrases become `NO_RECENT_DATA`, and advice requests become `ADVICE_REQUEST`, both with no
  rule;
- nothing outside the catalog appears, and the spec validates.

**`CandidateFinderEvalTest`**, 12 frozen specs in `src/test/resources/hunter-eval/finder-cases.json`. A run
passes if:
- at most 2 returned tickers are outside the universe (a measure of invented tickers);
- no candidate's own estimate fails a rule;
- `SourceGuard` and `AdviceGuard` remove nothing;
- no ticker from the case's "must not include" list appears. For example, a spec for high-margin
  software must not return utilities.

No automated check can tell whether an estimate is right. When the model changes, spot-check a sample
of estimates by hand against the companies' published figures. Record evaluation results in the table
below, as the trade analysis spec does.

| Date | Model | Suite | Runs passing |
|---|---|---|---|
| 2026-10-23 | `gpt-6-luna` (translator prompt `translator-1`) | Translator, 1 run each | 19 of 20: "improving cash flow and low debt" became NO_DATA instead of checks |
| 2026-10-23 | `gpt-6-luna` (translator prompt `translator-2`) | Translator, 3 runs each | 60 of 60 |
| 2026-10-23 | `gpt-6-luna` (finder prompt `finder-1`) | Finder, 3 runs each | 36 of 36, with no invented tickers, no estimate failing its own rule, and no guard removals |

`translator-2` says that qualities and trends general knowledge can judge are checks, never NO_DATA.
Results vary from run to run, so compare models over several runs.

## 10. Non-functional requirements

| Requirement | Value |
|---|---|
| Latency | Measured on 2026-10-23 with the example prompt: draft 7.9 s, run 17.8 s (7 candidates); a reused run makes no model call. If runs get too slow, try `reasoning_effort: none`, then a lower default `candidateLimit` |
| Timeouts | The app waits up to 120 s for the model (`HunterConfig`), and nginx up to 150 s for `/api/hunter/` (its default is 60 s) |
| Cost per run | One call: roughly 2k input and up to 8k output tokens, well under 1 cent at the prices in section 3. A reused run costs nothing |
| Rate limits | Drafts: 5 a minute per user, 20 overall. Runs: 3 a minute per user, 20 overall. Only calls that reach the model count: a draft from the cache and a reused run do not. Two more instances of `AnalysisRateLimiter`, in memory per app instance |
| Draft cache | In memory, keyed by the normalized prompt, model and prompt version, most recent 500 |
| Usage log | One line per draft and per run: specId, reused, candidates returned, dropped (not in universe, failed rules), guard removals, model, prompt and completion tokens, latency |
| Configuration | `OPENAI_API_KEY`; `HUNTER_OPENAI_MODEL` (default `gpt-6-luna`); `hunter.*` in `application.properties` |
| Outbound calls | Only the model API; no market data, SEC or news requests |
| Failure isolation | The app starts without an OpenAI key; Hunter endpoints that call the model then return 502, and saved specs, saved runs and the watchlist keep working |

### Known limitations

- **Estimates can be wrong, and the app can't tell.** Code checks the model's numbers against the
  thresholds, not against reality.
- **Knowledge ends on May 18, 2026.** Later earnings, deals, index changes and price moves are
  unknown.
- **Results can differ** when a spec changes, even slightly, because the model is asked again. An
  unchanged spec returns its saved run.
- **The universe list is a snapshot**, updated by hand.

## 11. Acceptance criteria

Built on 2026-10-23. The automated checks run with `mvn test` (backend) and `ng test` (frontend), and
all pass.

The docker-compose stack was stopped, so instead the built app ran against Postgres 16 and the live
model on 2026-10-23, with Kafka, Redis and the market service unreachable. One script walked the API
and every check passed:
- **Access:** no token gets 401, and another user's hunter is a 404.
- **Draft:** about 3 s; the same words came from the cache.
- **Run:** 10 matches in 22 s, all in the universe and all meeting their own estimates. Running it
  again unchanged reused the saved run in 0.02 s.
- **Watchlist:** adding gave 201 and then 200, and a ticker outside the universe gave 404.
- **Errors and deletes:** an invalid edit gave 400 with field errors, and deleting the hunter kept its
  watchlist item without a source.
- **Usage log:** one line per draft and per run.

### Backend

| # | Given / When / Then | Covered by |
|---|---|---|
| B1 | Given a prompt, when a draft is requested, then 200 with a spec that validates and uses only catalog metrics and sectors | `HunterEndpointTest.returnsADraftForTheLoggedInUser`, `SpecTranslatorTest.turnsTheReplyIntoAValidDraft` |
| B2 | Given a prompt with a time-based phrase, when it is drafted, then the phrase is a `NO_RECENT_DATA` clause and no rule comes from it | `SpecTranslatorTest.turnsTheReplyIntoAValidDraft`, `SpecTranslatorEvalTest`; live: `HunterLiveTest` |
| B3 | Given an unknown metric, a bad operator or too many rules, when a spec is saved, then 400 with a field error for each | `HunterSpecValidatorTest`, `HunterEndpointTest.reportsEachInvalidFieldWith400` |
| B4 | Given the model returns a ticker outside the universe, when the run is checked, then it is dropped and counted in `droppedCount` | `CandidateChecksTest.dropsTickersOutsideTheUniverseAndDuplicates` |
| B5 | Given an estimate that fails a threshold or is null, when the run is checked, then that candidate is dropped | `CandidateChecksTest.checksThresholdsAgainstTheModelsOwnEstimates`, `checksBetweenInclusively` |
| B6 | Given an enabled sector rule, when the run is checked, then candidates whose listed sector fails it are dropped | `CandidateChecksTest.usesTheListsSectorNotTheModels` |
| B7 | Given a reason or bullet with quotation marks or a document reference, when the run is checked, then it is removed | `SourceGuardTest`, `CandidateChecksTest.removesInventedSourcesAndAdviceButKeepsTheVerdict` |
| B8 | Given a reason or bullet with advice language, when the run is checked, then it is removed; a flagged spec name becomes "My hunter" | `AdviceGuardTest`, `SpecTranslatorTest.dropsWhatTheModelGotWrong` |
| B9 | Given verdicts MET, MET and PARTIAL, when the score is computed, then it is 83; with no enabled checks it is null | `MatchScorerTest` |
| B10 | Given a spec was run, when it is run again unchanged, then the saved run is returned and the model is not called; after any rule or model change, the model is called | `HunterServiceTest.reusesTheSavedRunWhileNothingChanged`, `asksAgainAfterAnyRuleOrModelChange` |
| B11 | Given the model fails, when a run is requested, then 502, the upstream error is not in the response, and nothing is saved | `HunterEndpointTest.returns502WithoutLeakingTheUpstreamError`, `HunterServiceTest.savesNothingWhenTheModelFails` |
| B12 | Given another user's spec ID, when it is read, run or deleted, then 404 | `HunterServiceTest.treatsAnotherUsersHunterAsNotFound`, `HunterEndpointTest.returns404ForAnUnknownHunterOrSomeoneElses` |
| B13 | Given a user made 3 runs this minute, when they run again, then 429 with `Retry-After` and the model is not called | `HunterServiceTest.countsOnlyRunsThatCallTheModelAgainstTheLimit`, `HunterEndpointTest.returns429WithRetryAfter` |
| B14 | Given any run, when it executes, then the request to the model includes the knowledge cutoff and no other outbound HTTP request is made | `CandidateFinderTest.sendsOnlyEnabledRulesTheCutoffAndHowManyToReturn` |
| B15 | Given no OpenAI key, when the app starts, then it runs, model calls return 502, and the watchlist works | Manual check, open. The app was seen to start and serve Hunter without Kafka, Redis or the market service |

The SQL in the three mappers and in `schema.sql` was run against Postgres 16 on 2026-10-23: the schema
applies twice without error, a second add keeps one watchlist row, the latest run and counts come
back right, pruning keeps the newest runs, and deleting a spec removes its runs and keeps its
watchlist items with no source.

### Watchlist

| # | Given / When / Then | Covered by |
|---|---|---|
| W1 | Given a universe ticker, when it is added twice, then the list has it once, with 201 then 200 | `WatchlistServiceTest.addsATickerOnceAndThenReportsItAsAlreadyThere`, `WatchlistEndpointTest.returns201WhenAddedAnd200WhenAlreadyThere` |
| W2 | Given a ticker outside the universe, when it is added, then 404 | `WatchlistServiceTest.rejectsATickerOutsideTheUniverse`, `WatchlistEndpointTest.returns404OutsideTheUniverse` |
| W3 | Given 100 items, when another is added, then 409 `LIMIT_REACHED` | `WatchlistServiceTest.refusesAHundredAndFirstStock`, `WatchlistEndpointTest.returns409WhenFull` |
| W4 | Given the spec that found an item is deleted, when the watchlist is read, then the item is still there with a null `sourceSpec` | `WatchlistServiceTest.listsItemsWithNamesFromTheUniverseAndKeepsThemWhenTheirHunterIsGone`; the Postgres check above |

### UI

| # | Given / When / Then | Covered by |
|---|---|---|
| U1 | Given a prompt, when Build hunter is pressed, then the card shows its filters, qualitative checks and "Not used" clauses | `hunter-page.component.spec.ts`, `hunter-card.component.spec.ts` |
| U2 | Given a prompt with "this month", when it is drafted, then "Not used" shows the knowledge-cutoff message | `hunter-card.component.spec.ts` |
| U3 | Given results, when they are shown, then the AI-knowledge badge and disclaimer appear, and each card has its score text, at most 3 bullets, and estimates labelled "AI estimate" with their period | `match-card.component.spec.ts`, `hunter-page.component.spec.ts` |
| U4 | Given a match, when Add to watchlist is pressed, then it shows "On watchlist ✓", the item appears on the Watchlist page, and pressing it again removes it | `match-card.component.spec.ts`, `hunter-page.component.spec.ts`, `watchlist-page.component.spec.ts`, `hunter.service.spec.ts` |
| U5 | Given an unchanged spec, when Run hunter is pressed, then the "Same rules as your run on {date}" message appears with the saved results | `hunter-page.component.spec.ts` |
| U6 | Given each error status in section 8.3, when it is returned, then the matching message is shown | `hunter.service.spec.ts`, `hunter-page.component.spec.ts` |
| U7 | Given any of the app's languages, when the pages are shown, then every label is in that language | `hunter.translations.spec.ts` (every key in every language in `LANGUAGES`, with matching placeholders) |
| U8 | Given the full docker-compose stack, when the example prompt in section 4.3 is run, then matches appear and the app log shows one model call and no other outbound request | Partly checked: the API run above showed one model call per run. The check in a browser on the docker-compose stack is open |

## 12. Build status

Done on 2026-10-23:

1. **Shared model client:** `ai/OpenAiJsonClient` now handles the HTTP, strict-schema and error
   handling. `OpenAiTradeAnalyzer` uses it, and its tests pass unchanged.
2. **Backend:**
   - **Package `com.neueda.leap.hunter`:** `catalog/`, `spec/`, `translate/`, `find/`, `check/`,
     `score/`, plus `HunterService` and `WatchlistService`.
   - **Data and endpoints:** the three mappers, the tables in `schema.sql`, `HunterController` and
     `WatchlistController`.
   - **Universe:** `src/main/resources/hunter/sp500.csv`.
   - **Configuration:** `hunter.*` in `application.properties`, plus `HUNTER_OPENAI_MODEL` in
     `docker-compose.yml` and `.env.example`.
   - **Tests:** unit and endpoint tests, `HunterLiveTest`, and the two evaluation suites.
3. **Frontend:** `hunter.model.ts`, `hunter.service.ts`, `watchlist.service.ts`, the lazy-loaded Hunter
   and Watchlist pages, the navbar items, translations in every app language, and component tests.
4. **nginx:** a 150 s read timeout for `/api/hunter/`.

Open:

- **Manual checks B15 and U8:** these need the docker-compose stack and a browser.
- **Translations:** a native-speaker check of the non-English text.
