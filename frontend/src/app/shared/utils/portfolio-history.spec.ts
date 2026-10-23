import { HistoryResponse } from '../../core/models/market.model';
import { combinePortfolioHistory } from './portfolio-history';

function makeHistory(ticker: string, closes: Record<string, number>): HistoryResponse {
  return {
    ticker,
    period: 'max',
    bars: Object.entries(closes).map(([date, close]) => ({
      date,
      open: close,
      high: close,
      low: close,
      close,
      volume: 0
    }))
  };
}

describe('combinePortfolioHistory', () => {
  it('replays quantities for each date and includes fully exited tickers', () => {
    const points = combinePortfolioHistory(
      [
        {
          ticker: 'AAPL',
          side: 'BUY',
          quantity: 10,
          executedAt: '2025-01-02T09:00:00Z'
        },
        {
          ticker: 'AAPL',
          side: 'SELL',
          quantity: 10,
          executedAt: '2025-01-04T09:00:00Z'
        },
        {
          ticker: 'MSFT',
          side: 'BUY',
          quantity: 2,
          executedAt: '2025-01-03T09:00:00Z'
        }
      ],
      [
        makeHistory('MSFT', {
          '2025-01-02': 200,
          '2025-01-03': 210,
          '2025-01-04': 220
        }),
        makeHistory('AAPL', {
          '2025-01-01': 100,
          '2025-01-02': 110,
          '2025-01-03': 120,
          '2025-01-04': 130
        })
      ]
    );

    expect(points).toEqual([
      { date: '2025-01-01', value: 0 },
      { date: '2025-01-02', value: 1100 },
      { date: '2025-01-03', value: 1620 },
      { date: '2025-01-04', value: 440 }
    ]);
  });

  it('orders trades by epoch time when timezone offsets differ', () => {
    const points = combinePortfolioHistory(
      [
        {
          ticker: 'AAPL',
          side: 'BUY',
          quantity: 10,
          executedAt: '2025-01-02T23:00:00-05:00'
        },
        {
          ticker: 'AAPL',
          side: 'SELL',
          quantity: 10,
          executedAt: '2025-01-03T00:00:00+09:00'
        }
      ],
      [
        makeHistory('AAPL', {
          '2025-01-02': 100,
          '2025-01-03': 110
        })
      ]
    );

    expect(points).toEqual([
      { date: '2025-01-02', value: -1000 },
      { date: '2025-01-03', value: 0 }
    ]);
  });
});
