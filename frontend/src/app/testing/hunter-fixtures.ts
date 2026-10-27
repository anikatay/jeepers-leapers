import { HunterCatalog, HunterMatch, HunterRun, HunterSpec } from '../core/models/hunter.model';

/** Test data for the Watchlist Hunter specs; never imported by app code. */
export const CATALOG: HunterCatalog = {
  metrics: [
    { id: 'marketCapUsdB', label: 'Market cap', unit: '$ bn', definition: 'Share price × shares outstanding' },
    { id: 'grossMarginPct', label: 'Gross margin', unit: '%', definition: 'Gross profit ÷ revenue' },
    { id: 'peRatio', label: 'P/E', unit: '×', definition: 'Share price ÷ earnings per share' },
  ],
  sectors: ['Information Technology', 'Health Care', 'Utilities'],
  model: 'gpt-6-luna',
  knowledgeCutoff: '2026-05-18',
  universeAsOf: '2026-10-23',
  universeSize: 503,
  limits: {
    maxPromptLength: 500, maxQuantRules: 8, maxCriteria: 5, maxCandidates: 15, defaultCandidates: 10,
    maxSpecs: 20, maxWatchlistItems: 100
  }
};

export function spec(overrides: Partial<HunterSpec> = {}): HunterSpec {
  return {
    specVersion: 1,
    id: null,
    name: 'AI infrastructure',
    prompt: 'Find high-margin AI infrastructure stocks that pulled back more than 10% this month',
    universe: 'SP500',
    quantRules: [{ id: 'q1', enabled: true, sourceText: 'high-margin', metric: 'grossMarginPct', op: 'gte', value: 50, value2: null }],
    sectorRule: null,
    criteria: [{ id: 's1', enabled: true, sourceText: 'AI infrastructure', statement: 'Sells products used to run AI data centers' }],
    unsupported: [{ sourceText: 'pulled back more than 10% this month', reason: 'NO_RECENT_DATA', message: 'from the server' }],
    candidateLimit: 10,
    ...overrides
  };
}

export function match(overrides: Partial<HunterMatch> = {}): HunterMatch {
  return {
    ticker: 'NVDA',
    companyName: 'Nvidia',
    gicsSector: 'Information Technology',
    matchScore: 83,
    whyItMatched: ['Designs GPUs used in AI data centers.', 'Sells networking for AI clusters.', 'Gross margin estimated at about 75% (FY2026).'],
    criteria: [{ criterionId: 's1', verdict: 'MET', reason: 'Its accelerators run most AI training.' }],
    estimates: [{ ruleId: 'q1', metric: 'grossMarginPct', value: 75, period: 'FY2026' }],
    onWatchlist: false,
    ...overrides
  };
}

export function run(overrides: Partial<HunterRun> = {}): HunterRun {
  return {
    runId: 'r1',
    specId: 'spec-1',
    ranAt: '2026-10-23T12:00:00Z',
    source: { model: 'gpt-6-luna', knowledgeCutoff: '2026-05-18' },
    matches: [match()],
    droppedCount: 0,
    disclaimer: 'server disclaimer',
    reused: false,
    ...overrides
  };
}
