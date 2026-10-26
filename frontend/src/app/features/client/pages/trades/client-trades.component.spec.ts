import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { EMPTY, Subject, of } from 'rxjs';
import { ClientTradesComponent } from './client-trades.component';
import { AuthService } from '../../../../core/services/auth.service';
import { TradesService } from '../../../../core/services/trades.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { MarketQuoteService } from '../../../../core/services/market-quote.service';
import { TradeAnalysisService } from '../../../../core/services/trade-analysis.service';
import { TradeResponse } from '../../../../core/models/trade.model';
import { AnalysisError, TradeAnalysis } from '../../../../core/models/trade-analysis.model';

const TRADES: TradeResponse[] = [
  {
    tradeId: 'trade-nvda', accountId: 'account-1', instrumentId: 'nvda', ticker: 'NVDA', name: 'NVIDIA Corp.',
    side: 'SELL', quantity: 8, executionPrice: 218.62, tradeValue: 1748.96, executedAt: '2026-10-19T15:00:00Z',
  },
  {
    tradeId: 'trade-aapl', accountId: 'account-1', instrumentId: 'aapl', ticker: 'AAPL', name: 'Apple Inc.',
    side: 'BUY', quantity: 10, executionPrice: 229, tradeValue: 2290, executedAt: '2026-10-18T15:00:00Z',
  },
];

const ANALYSIS: TradeAnalysis = {
  result: { verdict: 'GOOD', summary: 'You sold at a gain.', strengths: [], concerns: [], suggestions: [] },
  pnl: { kind: 'not-calculated' },
};

describe('ClientTradesComponent trade analysis', () => {
  let fixture: ComponentFixture<ClientTradesComponent>;
  let element: HTMLElement;
  let requests: Subject<TradeAnalysis>[];
  let analyze: ReturnType<typeof vi.fn<(tradeId: string) => Subject<TradeAnalysis>>>;

  beforeEach(async () => {
    localStorage.clear();
    requests = [];
    analyze = vi.fn((_tradeId: string) => {
      const request = new Subject<TradeAnalysis>();
      requests.push(request);
      return request;
    });

    await TestBed.configureTestingModule({
      imports: [ClientTradesComponent],
      providers: [
        { provide: AuthService, useValue: { session: signal({ accountId: 'account-1' }), token: 'test-token' } },
        { provide: TradesService, useValue: { getTradesByAccount: () => of(TRADES) } },
        { provide: PortfolioService, useValue: { getPortfolio: () => of([]) } },
        { provide: InstrumentService, useValue: { getAllInstruments: () => of([]) } },
        { provide: MarketDataService, useValue: { getHistory: () => EMPTY } },
        {
          provide: MarketQuoteService,
          useValue: { status: signal('offline'), quotes$: EMPTY, subscribe: vi.fn(), unsubscribe: vi.fn() },
        },
        { provide: TradeAnalysisService, useValue: { analyze } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ClientTradesComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    element = fixture.nativeElement;
  });

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const button = (ticker: string) =>
    [...element.querySelectorAll<HTMLButtonElement>('.analyze-button')]
      .find(b => b.getAttribute('aria-label')?.includes(` ${ticker} `))!;
  const dialog = () => element.querySelector('app-trade-analysis-dialog');

  async function press(ticker: string) {
    button(ticker).click();
    await settle();
  }

  async function closeDialog() {
    (element.querySelector('.close-button') as HTMLElement).click();
    await settle();
  }

  // U1
  it('gives every trade an Analyze button that names the trade', () => {
    const labels = [...element.querySelectorAll('.analyze-button')].map(b => b.getAttribute('aria-label'));

    expect(labels).toEqual([
      expect.stringMatching(/^Analyze NVDA Sell from Oct \d{1,2}, 2026$/),
      expect.stringMatching(/^Analyze AAPL Buy from Oct \d{1,2}, 2026$/),
    ]);
  });

  // U2
  it('opens the dialog in the loading state and disables the button until the request finishes', async () => {
    await press('NVDA');

    expect(analyze).toHaveBeenCalledWith('trade-nvda');
    expect(dialog()?.textContent).toContain('Analyzing your trade…');
    expect(button('NVDA').getAttribute('aria-disabled')).toBe('true');
    expect(button('AAPL').hasAttribute('aria-disabled')).toBe(false);

    requests[0].next(ANALYSIS);
    requests[0].complete();
    await settle();

    expect(dialog()?.textContent).toContain('You sold at a gain.');
    expect(button('NVDA').hasAttribute('aria-disabled')).toBe(false);
  });

  // U2
  it('ignores the button while its request is still running', async () => {
    await press('NVDA');
    await closeDialog();
    await press('NVDA');

    expect(analyze).toHaveBeenCalledTimes(1);
    expect(dialog()).toBeNull();
  });

  // U7
  it('shows a stored result again without a new request', async () => {
    await press('NVDA');
    requests[0].next(ANALYSIS);
    requests[0].complete();
    await settle();
    await closeDialog();

    await press('NVDA');

    expect(analyze).toHaveBeenCalledTimes(1);
    expect(dialog()?.textContent).toContain('You sold at a gain.');
  });

  // U10
  it('does not store errors, so pressing Analyze again retries', async () => {
    await press('NVDA');
    requests[0].error({ kind: 'unavailable' } satisfies AnalysisError);
    await settle();

    expect(dialog()?.textContent).toContain('Analysis is unavailable right now.');

    await closeDialog();
    await press('NVDA');

    expect(analyze).toHaveBeenCalledTimes(2);
    expect(dialog()?.textContent).toContain('Analyzing your trade…');
  });

  it('stores a result that arrives after the dialog was closed, without reopening it', async () => {
    await press('NVDA');
    await closeDialog();
    requests[0].next(ANALYSIS);
    requests[0].complete();
    await settle();

    expect(dialog()).toBeNull();

    await press('NVDA');
    expect(analyze).toHaveBeenCalledTimes(1);
    expect(dialog()?.textContent).toContain('You sold at a gain.');
  });

  // U11
  it('returns focus to the Analyze button that opened the dialog', async () => {
    await press('AAPL');
    expect(document.activeElement).toBe(element.querySelector('.close-button'));

    await closeDialog();

    expect(dialog()).toBeNull();
    expect(document.activeElement).toBe(button('AAPL'));
  });
});
