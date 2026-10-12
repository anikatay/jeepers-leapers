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