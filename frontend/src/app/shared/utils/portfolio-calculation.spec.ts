import { TradeResponse } from '../../core/models/trade.model';
import { calculateAverageCostBasis, calculateHoldingGain } from './portfolio-calculation';

const makeTrade = (overrides: Partial<TradeResponse>): TradeResponse => ({
  tradeId: 'trade-1',
  accountId: 'account-1',
  instrumentId: 'instrument-1',
  ticker: 'AAPL',
  name: 'Apple Inc.',
  side: 'BUY',
  quantity: 1,
  executionPrice: 100,
  tradeValue: 100,
  executedAt: '2026-01-01T10:00:00Z',
  ...overrides
});

describe('portfolio calculation', () => {
  it('keeps the average cost basis for the remaining shares after a partial sale', () => {
    const averageCost = calculateAverageCostBasis('instrument-1', [
      makeTrade({ tradeId: 'buy-1', quantity: 10, executionPrice: 100, tradeValue: 1000 }),
      makeTrade({
        tradeId: 'buy-2',
        quantity: 10,
        executionPrice: 140,
        tradeValue: 1400,
        executedAt: '2026-01-02T10:00:00Z'
      }),
      makeTrade({
        tradeId: 'sell-1',
        side: 'SELL',
        quantity: 5,
        executionPrice: 200,
        tradeValue: 1000,
        executedAt: '2026-01-03T10:00:00Z'
      })
    ]);

    expect(averageCost).toBe(120);
    expect(calculateHoldingGain(15, 150, averageCost)).toEqual({
      gain: 450,
      gainPct: 25
    });
  });

  it('does not report P/L when the cost basis is unavailable', () => {
    expect(calculateHoldingGain(10, 150, 0)).toEqual({
      gain: 0,
      gainPct: 0
    });
  });
});
