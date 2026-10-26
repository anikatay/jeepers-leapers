import { HistoryResponse } from '../../core/models/market.model';
import { combinePortfolioHistory, normalizeIntradayBaseline } from './portfolio-history';

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

  it('carries the last close across dates without a bar', () => {
    const points = combinePortfolioHistory(
      [
        {
          ticker: 'TSLA',
          side: 'BUY',
          quantity: 1,
          executedAt: '2025-01-02T09:00:00Z'
        },
        {
          ticker: 'BTC-USD',
          side: 'BUY',
          quantity: 1,
          executedAt: '2025-01-02T09:00:00Z'
        }
      ],
      [
        makeHistory('TSLA', {
          '2025-01-03': 100,
          '2025-01-06': 110
        }),
        makeHistory('BTC-USD', {
          '2025-01-03': 50000,
          '2025-01-04': 51000,
          '2025-01-05': 52000,
          '2025-01-06': 53000
        })
      ]
    );

    expect(points).toEqual([
      { date: '2025-01-03', value: 50100 },
      { date: '2025-01-04', value: 51100 },
      { date: '2025-01-05', value: 52100 },
      { date: '2025-01-06', value: 53110 }
    ]);
  });

  it('replays quantities at intraday bar timestamps', () => {
    const points = combinePortfolioHistory(
      [
        {
          ticker: 'AAPL',
          side: 'BUY',
          quantity: 2,
          executedAt: '2025-01-02T14:31:00Z'
        }
      ],
      [
        makeHistory('AAPL', {
          '2025-01-02T14:30:00Z': 100,
          '2025-01-02T14:31:00Z': 105,
          '2025-01-02T14:32:00Z': 110
        })
      ]
    );

    expect(points).toEqual([
      { date: '2025-01-02T14:30:00Z', value: 0 },
      { date: '2025-01-02T14:31:00Z', value: 210 },
      { date: '2025-01-02T14:32:00Z', value: 220 }
    ]);
  });

  it('values a ticker whose series starts late from its earliest close', () => {
    const points = combinePortfolioHistory(
      [
        { ticker: 'AAPL', side: 'BUY', quantity: 1, executedAt: '2025-01-01T09:00:00Z' },
        { ticker: 'MSFT', side: 'BUY', quantity: 1, executedAt: '2025-01-01T09:00:00Z' }
      ],
      [
        makeHistory('AAPL', { '2025-01-01': 100, '2025-01-02': 100 }),
        // No bar on 01-01, so without seeding MSFT would count as zero and the total
        // would ramp from 100 to 300 purely because its history starts a day later.
        makeHistory('MSFT', { '2025-01-02': 200 })
      ]
    );

    expect(points).toEqual([
      { date: '2025-01-01', value: 300 },
      { date: '2025-01-02', value: 300 }
    ]);
  });
});

describe('normalizeIntradayBaseline', () => {
  const session = (bars: HistoryResponse['bars']): HistoryResponse => ({
    ticker: 'AAPL',
    period: '1d',
    bars
  });

  const flatBar = (date: string, price: number, volume = 0) => ({
    date,
    open: price,
    high: price,
    low: price,
    close: price,
    volume
  });

  it('re-stamps the synthetic midnight baseline to just before the first real bar', () => {
    const result = normalizeIntradayBaseline(session([
      flatBar('2025-01-02T00:00:00Z', 95),
      { date: '2025-01-02T14:30:00Z', open: 100, high: 104, low: 99, close: 102, volume: 1_000 }
    ]));

    expect(result.bars[0].date).toBe('2025-01-02T14:29:00.000Z');
    expect(result.bars[0].close).toBe(95);
    expect(result.bars[1].date).toBe('2025-01-02T14:30:00Z');
  });

  it('leaves a genuine midnight bar alone when it carries volume', () => {
    const bars = [
      flatBar('2025-01-02T00:00:00Z', 95, 500),
      { date: '2025-01-02T00:01:00Z', open: 95, high: 96, low: 94, close: 96, volume: 400 }
    ];

    expect(normalizeIntradayBaseline(session(bars)).bars).toEqual(bars);
  });

  it('leaves a session without a midnight bar alone', () => {
    const bars = [
      { date: '2025-01-02T14:30:00Z', open: 100, high: 104, low: 99, close: 102, volume: 1_000 },
      { date: '2025-01-02T14:31:00Z', open: 102, high: 105, low: 101, close: 104, volume: 900 }
    ];

    expect(normalizeIntradayBaseline(session(bars)).bars).toEqual(bars);
  });
});
