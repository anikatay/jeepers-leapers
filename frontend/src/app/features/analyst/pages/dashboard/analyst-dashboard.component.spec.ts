import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { AnalystDataSource, AnalystSnapshot, PriceBar } from '../../data-access/analyst-data-source';
import { AnalystDashboardComponent } from './analyst-dashboard.component';

describe('AnalystDashboardComponent lazy history', () => {
  function setup() {
    const histories = new Map<string, Subject<PriceBar[]>>();
    const snapshot: AnalystSnapshot = {
      asOf: '2026-10-26',
      instruments: ['AAPL', 'MSFT', 'NVDA'].map(ticker => ({
        instrumentId: ticker, ticker, name: ticker, currentPrice: 100
      })),
      trades: [],
      history: {},
      intraday: {}
    };
    const requested: string[] = [];
    TestBed.configureTestingModule({
      providers: [{
        provide: AnalystDataSource,
        useValue: {
          load: () => of(snapshot),
          loadHistory: (ticker: string) => {
            requested.push(ticker);
            const response = new Subject<PriceBar[]>();
            histories.set(ticker, response);
            return response;
          }
        }
      }]
    });
    const component = TestBed.runInInjectionContext(
      () => new AnalystDashboardComponent(TestBed.inject(AnalystDataSource))
    );
    return { component, requested, histories };
  }

  it('shows the snapshot while only selected histories load, then reuses loaded history', () => {
    const { component, requested, histories } = setup();
    expect(component.snapshot()).not.toBeNull();
    expect(requested).toEqual(['AAPL', 'MSFT']);
    histories.get('AAPL')!.next([]);
    component.toggleSeries('NVDA');
    expect(requested).toEqual(['AAPL', 'MSFT', 'NVDA']);
    component.toggleSeries('AAPL');
    component.toggleSeries('AAPL');
    expect(requested).toEqual(['AAPL', 'MSFT', 'NVDA']);
  });

  it('reports chart failure without hiding the snapshot and allows retry', () => {
    const { component, requested, histories } = setup();
    histories.get('AAPL')!.error(new Error('History unavailable'));
    expect(component.historyErrors()).toEqual(['AAPL']);
    expect(component.error()).toBe(false);
    expect(component.snapshot()).not.toBeNull();
    component.loadSelectedHistory();
    expect(requested).toEqual(['AAPL', 'MSFT', 'AAPL']);
  });
});
