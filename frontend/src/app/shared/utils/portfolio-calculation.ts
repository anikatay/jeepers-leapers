import { Holding } from '../../core/models/holding.model';
import { Instrument } from '../../core/models/instrument.model';
import { Trade } from '../../core/models/trade.model';

export function calculateCurrentPortfolioValue(holdings: Holding[], instruments: Instrument[]): number {
  let total = 0;
  for(const item of holdings){
    let instrument = instruments.find(i => i.instrumentId === item.instrumentId);
    if (item.instrumentId === instrument?.instrumentId){
      total += item.quantity * (instrument.currentPrice || 0);
    }
  }
  return total;
}

export function calculateAccountProfit(holdings: Holding[], instruments: Instrument[], trades: Trade[]): number {
  const currentValue = calculateCurrentPortfolioValue(holdings, instruments);

  const totalBought = trades
    .filter(t => t.side === 'BUY')
    .reduce((sum, t) => sum + t.tradeValue, 0);

  const totalSold = trades
    .filter(t => t.side === 'SELL')
    .reduce((sum, t) => sum + t.tradeValue, 0);

  return currentValue + totalSold - totalBought;
}