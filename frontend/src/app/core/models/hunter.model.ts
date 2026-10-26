// Mirrors com.neueda.leap.hunter (docs/specs/watchlist-hunter.md, sections 4–7)

export type MetricId =
  | 'marketCapUsdB' | 'revenueGrowthPct' | 'grossMarginPct' | 'operatingMarginPct'
  | 'fcfMarginPct' | 'peRatio' | 'priceToSales';

export type NumericOp = 'gt' | 'gte' | 'lt' | 'lte' | 'between';
export type SectorOp = 'in' | 'notIn';
export type UnsupportedReason = 'NO_RECENT_DATA' | 'NO_DATA' | 'AMBIGUOUS' | 'ADVICE_REQUEST' | 'OUT_OF_UNIVERSE';
export type CheckVerdict = 'MET' | 'PARTIAL' | 'NOT_MET' | 'UNKNOWN';

export interface QuantRule {
  id: string;
  enabled: boolean;
  sourceText: string | null;
  metric: MetricId;
  op: NumericOp;
  value: number;
  value2: number | null;
}

export interface SectorRule {
  id: string;
  enabled: boolean;
  sourceText: string | null;
  op: SectorOp;
  values: string[];
}

export interface Criterion {
  id: string;
  enabled: boolean;
  sourceText: string | null;
  statement: string;
}

export interface UnsupportedClause {
  sourceText: string | null;
  reason: UnsupportedReason;
  message: string;
}

export interface HunterSpec {
  specVersion: 1;
  id: string | null;
  name: string;
  prompt: string;
  universe: 'SP500';
  quantRules: QuantRule[];
  sectorRule: SectorRule | null;
  criteria: Criterion[];
  unsupported: UnsupportedClause[];
  candidateLimit: number;
}

export interface MetricInfo {
  id: MetricId;
  label: string;
  unit: string;
  definition: string;
}

export interface HunterCatalog {
  metrics: MetricInfo[];
  sectors: string[];
  model: string;
  knowledgeCutoff: string;
  universeAsOf: string;
  universeSize: number;
  limits: {
    maxPromptLength: number;
    maxQuantRules: number;
    maxCriteria: number;
    maxCandidates: number;
    defaultCandidates: number;
    maxSpecs: number;
    maxWatchlistItems: number;
  };
}

export interface HunterSpecSummary {
  specId: string;
  name: string;
  updatedAt: string;
  lastRunAt: string | null;
  lastMatchCount: number | null;
}

export interface CriterionResult {
  criterionId: string;
  verdict: CheckVerdict;
  reason: string | null;
}

export interface EstimateValue {
  ruleId: string;
  metric: MetricId;
  value: number;
  period: string | null;
}

export interface HunterMatch {
  ticker: string;
  companyName: string;
  gicsSector: string;
  matchScore: number | null;
  whyItMatched: string[];
  criteria: CriterionResult[];
  estimates: EstimateValue[];
  onWatchlist: boolean;
}

export interface HunterRun {
  runId: string;
  specId: string;
  ranAt: string;
  source: { model: string; knowledgeCutoff: string };
  matches: HunterMatch[];
  droppedCount: number;
  disclaimer: string;
  reused: boolean;
}

export interface WatchlistItem {
  ticker: string;
  companyName: string | null;
  gicsSector: string | null;
  addedAt: string;
  sourceSpec: { id: string; name: string } | null;
}

export interface HunterFieldError {
  field: string;
  message: string;
}

export type HunterErrorKind =
  | 'invalid' | 'session-expired' | 'not-found' | 'limit-reached' | 'rate-limited' | 'unavailable' | 'unknown';

export interface HunterError {
  kind: HunterErrorKind;
  retryAfterSeconds?: number;
  fieldErrors?: HunterFieldError[];
}
