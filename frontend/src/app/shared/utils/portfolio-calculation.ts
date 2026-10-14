import { HoldingResponse } from '../../core/models/holding.model';
import { InstrumentResponse } from '../../core/models/instrument.model';
import { TradeResponse } from '../../core/models/trade.model';

export function calculateCurrentPortfolioValue(holdings: HoldingResponse[], instruments: InstrumentResponse[]): number {
  let total = 0;
  for(const item of holdings){
    let instrument = instruments.find(i => i.instrumentId === item.instrumentId);
    if (item.instrumentId === instrument?.instrumentId){
      total += item.quantity * (instrument.currentPrice || 0);
    }
  }
  return total;
}

export function calculateAccountProfit(holdings: HoldingResponse[], instruments: InstrumentResponse[], trades: TradeResponse[]): number {
  const currentValue = calculateCurrentPortfolioValue(holdings, instruments);

  const totalBought = trades
    .filter(t => t.side === 'BUY')
    .reduce((sum, t) => sum + t.tradeValue, 0);

  const totalSold = trades
    .filter(t => t.side === 'SELL')
    .reduce((sum, t) => sum + t.tradeValue, 0);

  return currentValue + totalSold - totalBought;
}

export function calculateAverageCostBasis(instrumentId: string, trades: TradeResponse[]): number {
  const instrumentTrades = trades
    .filter(t => t.instrumentId === instrumentId)
    .slice()
    .sort((a, b) => new Date(a.executedAt).getTime() - new Date(b.executedAt).getTime());

  let qty = 0;
  let totalCost = 0;

  for (const t of instrumentTrades) {
    if (t.side === 'BUY') {
      totalCost += t.quantity * t.executionPrice;
      qty += t.quantity;
    } else if (qty > 0) {
      const avgCost = totalCost / qty;
      const sellQty = Math.min(t.quantity, qty);
      totalCost -= avgCost * sellQty;
      qty -= sellQty;
    }
  }

  return qty > 0 ? totalCost / qty : 0;
}

export function calculateHoldingGain(
  quantity: number,
  currentPrice: number,
  avgCostBasis: number
): { gain: number; gainPct: number } {
  const gain = (currentPrice - avgCostBasis) * quantity;
  const gainPct = avgCostBasis > 0 ? ((currentPrice - avgCostBasis) / avgCostBasis) * 100 : 0;
  return { gain, gainPct };
}
