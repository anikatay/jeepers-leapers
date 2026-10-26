import { MarketHeatmapSector, PriceBar } from '../../core/models/market.model';
import { selectSectorMovers } from './sector-movers';

const QUOTE_TIME = '2025-01-10T15:00:00Z';

function bars(...closes: Array<[string, number]>): PriceBar[] {
  return closes.map(([date, close]) => ({
    date,
    open: close,
    high: close,
    low: close,
    close,
    volume: 0
  }));
}

/** One prior close on the 9th, so a 1D change is measured against it. */
function history(close: number): PriceBar[] {
  return bars(['2025-01-09', close]);
}

function sector(id: string, tickers: Array<[string, number | null]>): MarketHeatmapSector {
  return {
    id,
    name: `${id} sector`,
    tickers: tickers.map(([ticker, price], index) => ({
      ticker,
      rank: index + 1,
      price,
      quoteTime: price === null ? null : QUOTE_TIME,
      receivedTime: QUOTE_TIME,
      source: 'test',
      status: 'LIVE' as const
    }))
  };
}

describe('selectSectorMovers', () => {
  it('takes the three best and the single worst performer per sector', () => {
    const sectors = [
      sector('TECH', [['AAA', 110], ['BBB', 105], ['CCC', 120], ['DDD', 100], ['EEE', 90]])
    ];
    const histories = new Map<string, PriceBar[] | null>([
      ['AAA', history(100)],  // +10%
      ['BBB', history(100)],  // +5%
      ['CCC', history(100)],  // +20%
      ['DDD', history(100)],  //   0%
      ['EEE', history(100)]   // -10%
    ]);

    const movers = selectSectorMovers(sectors, histories, '1D');

    expect(movers.map(m => m.ticker)).toEqual(['CCC', 'AAA', 'BBB', 'EEE']);
    expect(movers.map(m => m.laggard)).toEqual([false, false, false, true]);
    expect(movers[0].change).toBeCloseTo(20);
    expect(movers[3].change).toBeCloseTo(-10);
  });

  it('keeps sectors separate and in the order given', () => {
    const sectors = [
      sector('ENERGY', [['XOM', 110], ['CVX', 90]]),
      sector('TECH', [['AAPL', 120], ['MSFT', 80]])
    ];
    const histories = new Map<string, PriceBar[] | null>([
      ['XOM', history(100)],
      ['CVX', history(100)],
      ['AAPL', history(100)],
      ['MSFT', history(100)]
    ]);

    const movers = selectSectorMovers(sectors, histories, '1D');

    expect(movers.map(m => `${m.sectorId}:${m.ticker}`)).toEqual([
      'ENERGY:XOM', 'ENERGY:CVX', 'TECH:AAPL', 'TECH:MSFT'
    ]);
  });

  it('never repeats a ticker as both leader and laggard', () => {
    const sectors = [sector('SMALL', [['ONE', 110], ['TWO', 90]])];
    const histories = new Map<string, PriceBar[] | null>([
      ['ONE', history(100)],
      ['TWO', history(100)]
    ]);

    const movers = selectSectorMovers(sectors, histories, '1D');

    expect(movers.map(m => m.ticker)).toEqual(['ONE', 'TWO']);
    expect(new Set(movers.map(m => m.ticker)).size).toBe(movers.length);
  });

  it('drops a single-ticker sector to the leader slot only', () => {
    const sectors = [sector('SOLO', [['ONLY', 105]])];
    const histories = new Map<string, PriceBar[] | null>([['ONLY', history(100)]]);

    const movers = selectSectorMovers(sectors, histories, '1D');

    expect(movers).toHaveLength(1);
    expect(movers[0]).toMatchObject({ ticker: 'ONLY', laggard: false });
  });

  it('omits tickers whose change cannot be established rather than calling them flat', () => {
    const sectors = [sector('MIXED', [['GOOD', 110], ['NOHIST', 110], ['NOPRICE', null]])];
    const histories = new Map<string, PriceBar[] | null>([
      ['GOOD', history(100)],
      ['NOHIST', null]
    ]);

    const movers = selectSectorMovers(sectors, histories, '1D');

    expect(movers.map(m => m.ticker)).toEqual(['GOOD']);
  });

  it('returns nothing for a sector with no usable quotes', () => {
    const sectors = [sector('DARK', [['AAA', null], ['BBB', null]])];

    expect(selectSectorMovers(sectors, new Map(), '1D')).toEqual([]);
  });

  it('honours custom top and bottom counts', () => {
    const sectors = [
      sector('TECH', [['AAA', 150], ['BBB', 140], ['CCC', 130], ['DDD', 120], ['EEE', 60]])
    ];
    const histories = new Map<string, PriceBar[] | null>(
      ['AAA', 'BBB', 'CCC', 'DDD', 'EEE'].map(t => [t, history(100)])
    );

    const movers = selectSectorMovers(sectors, histories, '1D', { top: 1, bottom: 2 });

    expect(movers.map(m => m.ticker)).toEqual(['AAA', 'DDD', 'EEE']);
    expect(movers.map(m => m.laggard)).toEqual([false, true, true]);
  });
});
