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

export function calculateAllTimePortfolioValue(holdings: Holding[], instruments: Instrument[], trades: Trade[]): number {
  const currentValue = calculateCurrentPortfolioValue(holdings, instruments);
  let soldItemsValue = 0;
  for(const item of trades){
    if(item.side === "SELL"){
      soldItemsValue += item.tradeValue;
    }
  }
  return currentValue + soldItemsValue;
}

export function calculateAccountProfit(holdings: Holding[], instruments: Instrument[], trades: Trade[]): number {
  const currentValue = calculateCurrentPortfolioValue(holdings, instruments);
  let executedValue = 0;  // Total spent on all BUY trades
  let soldItemProfit = 0; // Profit from SELL trades

  // Group by instrumentId
  const tradesByInstrument = new Map<string, Trade[]>();
  trades.forEach(trade => {
    if (!tradesByInstrument.has(trade.instrumentId)) {
      tradesByInstrument.set(trade.instrumentId, []);
    }
    tradesByInstrument.get(trade.instrumentId)!.push(trade);
  });

  // For each instrument, calculate profit
  for (const instrumentTrades of tradesByInstrument.values()) {
    const buys = instrumentTrades.filter(t => t.side === 'BUY');
    const sells = instrumentTrades.filter(t => t.side === 'SELL');

    // Sum all BUY execution values
    executedValue += buys.reduce((sum, buy) => sum + buy.tradeValue, 0);

    // Profit from SELL trades: (sell value) - (corresponding buy value)
    sells.forEach(sell => {
      soldItemProfit += sell.tradeValue;
    });

    // Subtract cost of sold items
    sells.forEach(sell => {
      const matchingBuy = buys.find(b => new Date(b.executedAt) < new Date(sell.executedAt));
      if (matchingBuy) {
        soldItemProfit -= matchingBuy.tradeValue * (sell.quantity / matchingBuy.quantity);
      }
    });
  }

  // Formula: currentValue - executedValue + soldItemProfit
  const totalProfit = currentValue - executedValue + soldItemProfit;
  return totalProfit;
}