import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AnalysedTrade, TradeAnalysisDialogComponent } from './trade-analysis-dialog.component';
import { LANGUAGES, TranslationService } from '../../../../../core/services/translation.service';
import {
  AnalysisError,
  PnlStatus,
  TradeAnalysis,
  TradeAnalysisDialogState,
  Verdict,
} from '../../../../../core/models/trade-analysis.model';

const TRADE: AnalysedTrade = { symbol: 'NVDA', type: 'Sell', qty: 8, date: 'Oct 19, 2026' };

function analysis(verdict: Verdict = 'GOOD', pnl: PnlStatus = { kind: 'calculated', asOf: '2026-10-20T14:10:00Z' }): TradeAnalysis {
  return {
    result: {
      verdict,
      summary: 'You sold 8 NVDA shares at a 3.48% gain.',
      strengths: ['The sale was small against your portfolio'],
      concerns: [],
      suggestions: ['Keep a similar position size'],
    },
    pnl,
  };
}

describe('TradeAnalysisDialogComponent', () => {
  let fixture: ComponentFixture<TradeAnalysisDialogComponent>;
  let element: HTMLElement;
  let closed: number;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [TradeAnalysisDialogComponent] });
  });

  async function render(state: TradeAnalysisDialogState) {
    fixture = TestBed.createComponent(TradeAnalysisDialogComponent);
    fixture.componentRef.setInput('trade', TRADE);
    fixture.componentRef.setInput('state', state);
    closed = 0;
    fixture.componentInstance.closed.subscribe(() => closed++);
    fixture.detectChanges();
    await fixture.whenStable();
    element = fixture.nativeElement;
  }

  const text = (selector: string) => element.querySelector(selector)?.textContent?.trim() ?? null;
  const showError = (error: AnalysisError) => render({ status: 'error', error });

  it('is a labelled modal dialog naming the trade', async () => {
    await render({ status: 'loading' });

    const dialog = element.querySelector('[role="dialog"]')!;
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(element.querySelector(`#${dialog.getAttribute('aria-labelledby')}`)?.textContent).toContain('Trade analysis');
    expect(text('.analysis-subtitle')).toBe('NVDA · Sell · 8 · Oct 19, 2026');
  });

  // U2
  it('shows the loading state in a live region', async () => {
    await render({ status: 'loading' });

    expect(element.querySelector('[aria-live="polite"]')?.textContent).toContain('Analyzing your trade…');
    expect(element.querySelector('.verdict')).toBeNull();
  });

  // U4
  it('shows the verdict, summary, non-empty lists and disclaimer', async () => {
    await render({ status: 'done', analysis: analysis() });

    expect(text('.verdict')).toBe('Good decision');
    expect(text('.analysis-summary')).toBe('You sold 8 NVDA shares at a 3.48% gain.');
    const headings = [...element.querySelectorAll('.analysis-section h3')].map(h => h.textContent?.trim());
    expect(headings).toEqual(['Strengths', 'Suggestions']);
    expect(element.querySelectorAll('.analysis-section li')).toHaveLength(2);
    expect(text('.analysis-disclaimer')).toBe('Automated review, not financial advice.');
  });

  // U4
  it.each([
    ['GOOD', 'Good decision', 'verdict-good'],
    ['MIXED', 'Mixed', 'verdict-mixed'],
    ['POOR', 'Poor decision', 'verdict-poor'],
    ['TOO_EARLY', 'Too early to tell', 'verdict-too-early'],
  ] as const)('labels and colours a %s verdict', async (verdict, label, cssClass) => {
    await render({ status: 'done', analysis: analysis(verdict) });

    const badge = element.querySelector('.verdict')!;
    expect(badge.textContent?.trim()).toBe(label);
    expect(badge.classList).toContain('verdict');
    expect(badge.classList).toContain(cssClass);
  });

  // U5
  it('says when the P&L was calculated', async () => {
    await render({ status: 'done', analysis: analysis() });

    expect(text('.analysis-pnl')).toMatch(/^P&L as of Oct 20, 2026, \d{1,2}:\d{2} (AM|PM)$/);
  });

  // U6
  it('says the P&L is not calculated yet', async () => {
    await render({ status: 'done', analysis: analysis('TOO_EARLY', { kind: 'not-calculated' }) });

    expect(text('.analysis-pnl')).toBe('P&L not calculated yet. This review does not include profit or loss.');
  });

  // U6
  it('leaves out the P&L line when it is unknown, but keeps the disclaimer', async () => {
    await render({ status: 'done', analysis: analysis('GOOD', { kind: 'unknown' }) });

    expect(element.querySelector('.analysis-pnl')).toBeNull();
    expect(element.querySelector('.analysis-disclaimer')).not.toBeNull();
  });

  // U8, U9, U10
  it.each([
    [{ kind: 'session-expired' }, 'Your session has expired. Please log in again.'],
    [{ kind: 'not-found' }, 'This trade could not be found.'],
    [{ kind: 'rate-limited', retryAfterSeconds: 41 }, 'Too many analyses. Try again in 41 seconds.'],
    [{ kind: 'rate-limited', retryAfterSeconds: 1 }, 'Too many analyses. Try again in 1 second.'],
    [{ kind: 'unavailable' }, 'Analysis is unavailable right now. Please try again later.'],
    [{ kind: 'unknown' }, 'Something went wrong. Please try again.'],
  ] as const)('shows the message for a %o error', async (error, message) => {
    await showError(error);

    expect(text('[role="alert"]')).toBe(message);
    expect(element.querySelector('.analysis-disclaimer')).toBeNull();
  });

  // U11
  it('moves focus to the close button when it opens', async () => {
    await render({ status: 'loading' });

    expect(document.activeElement).toBe(element.querySelector('.close-button'));
  });

  // U11
  it('closes on Esc, the close button and the backdrop, but not on a click inside', async () => {
    await render({ status: 'done', analysis: analysis() });

    (element.querySelector('.analysis-summary') as HTMLElement).click();
    expect(closed).toBe(0);

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    (element.querySelector('.close-button') as HTMLElement).click();
    (element.querySelector('.analysis-overlay') as HTMLElement).click();
    expect(closed).toBe(3);
  });

  // U12
  it('follows the selected language', async () => {
    await render({ status: 'done', analysis: analysis() });

    TestBed.inject(TranslationService).setLanguage('es');
    fixture.detectChanges();

    expect(text('.analysis-title')).toBe('Análisis de la operación');
    expect(text('.verdict')).toBe('Buena decisión');
    expect(text('.analysis-subtitle')).toBe('NVDA · Vender · 8 · Oct 19, 2026');
  });

  // U9, U12
  it('words the wait for the selected language', async () => {
    TestBed.inject(TranslationService).setLanguage('es');
    await showError({ kind: 'rate-limited', retryAfterSeconds: 1 });

    expect(text('[role="alert"]')).toBe('Demasiados análisis. Inténtalo de nuevo en 1 segundo.');
  });

  // U12
  it('has every label and message in all 10 languages', () => {
    const translation = TestBed.inject(TranslationService);
    const keys = [
      'column', 'analyze', 'analyze_label', 'title', 'close', 'loading',
      'verdict.GOOD', 'verdict.MIXED', 'verdict.POOR', 'verdict.TOO_EARLY',
      'strengths', 'concerns', 'suggestions', 'pnl_as_of', 'pnl_not_calculated', 'disclaimer',
      'error.session_expired', 'error.not_found', 'error.rate_limited', 'error.unavailable', 'error.unknown',
    ].map(key => `trades.analysis.${key}`);
    const placeholders = (key: string) => (translation.translate(key).match(/\{\w+\}/g) ?? []).sort();

    translation.setLanguage('en');
    const englishPlaceholders = new Map(keys.map(key => [key, placeholders(key)]));

    const missing: string[] = [];
    for (const { code } of LANGUAGES) {
      translation.setLanguage(code);
      for (const key of keys) {
        const value = translation.translate(key);
        if (!value || value === key) {
          missing.push(`${code}: ${key}`);
        } else if (placeholders(key).join() !== englishPlaceholders.get(key)!.join()) {
          missing.push(`${code}: ${key} has placeholders ${placeholders(key)}`);
        }
      }
    }
    expect(missing).toEqual([]);
  });
});
