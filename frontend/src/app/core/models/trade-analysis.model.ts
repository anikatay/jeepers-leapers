// Mirrors com.neueda.leap.analysis.TradeAnalysisResult
export type Verdict = 'GOOD' | 'MIXED' | 'POOR' | 'TOO_EARLY';

export interface TradeAnalysisResult {
  verdict: Verdict;
  summary: string;
  strengths: string[];
  concerns: string[];
  suggestions: string[];
}

/** The only part of GET /api/trades/{id}/analysis/context the UI reads. */
export interface TradeAnalysisContext {
  outcome: { asOf: string } | null;
}

/** Whether the review included P&L: `unknown` when the context request failed. */
export type PnlStatus =
  | { kind: 'calculated'; asOf: string }
  | { kind: 'not-calculated' }
  | { kind: 'unknown' };

export interface TradeAnalysis {
  result: TradeAnalysisResult;
  pnl: PnlStatus;
}

export type AnalysisErrorKind = 'session-expired' | 'not-found' | 'rate-limited' | 'unavailable' | 'unknown';

export interface AnalysisError {
  kind: AnalysisErrorKind;
  retryAfterSeconds?: number;
}

export type TradeAnalysisDialogState =
  | { status: 'loading' }
  | { status: 'done'; analysis: TradeAnalysis }
  | { status: 'error'; error: AnalysisError };
