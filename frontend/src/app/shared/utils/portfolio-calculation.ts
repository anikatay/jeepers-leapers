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
  let totalProfit = 0;
  const currentValue = calculateCurrentPortfolioValue(holdings, instruments);
  let soldItemsValue = 0;
  for(const item of trades){
    if(item.side === "SELL"){
      soldItemsValue += item.tradeValue;
    }
  }
  totalProfit = currentValue + soldItemsValue;


  // // Group trades by instrumentId
  // const tradesByInstrument = new Map<string, Trade[]>();
  // trades.forEach(trade => {
  //   if (!tradesByInstrument.has(trade.instrumentId)) {
  //     tradesByInstrument.set(trade.instrumentId, []);
  //   }
  //   tradesByInstrument.get(trade.instrumentId)!.push(trade);
  // });

  // // For each instrument, calculate realized + unrealized profit
  // tradesByInstrument.forEach((instrumentTrades, instrumentId) => {
  //   // Sort by executedAt (FIFO matching)
  //   const sorted = [...instrumentTrades].sort((a, b) => 
  //     new Date(a.executedAt).getTime() - new Date(b.executedAt).getTime()
  //   );

  //   const buys = sorted.filter(t => t.side === 'BUY');
  //   const sells = sorted.filter(t => t.side === 'SELL');

  //   // Calculate REALIZED profit from sold items
  //   let buyIdx = 0;
  //   let buyQtyRemaining = buys[0]?.quantity || 0;

  //   sells.forEach(sell => {
  //     let sellQtyRemaining = sell.quantity;

  //     while (sellQtyRemaining > 0 && buyIdx < buys.length) {
  //       const matchQty = Math.min(sellQtyRemaining, buyQtyRemaining);
  //       const profit = (sell.executionPrice - buys[buyIdx].executionPrice) * matchQty;
  //       totalProfit += profit;

  //       sellQtyRemaining -= matchQty;
  //       buyQtyRemaining -= matchQty;

  //       if (buyQtyRemaining === 0) {
  //         buyIdx++;
  //         buyQtyRemaining = buys[buyIdx]?.quantity || 0;
  //       }
  //     }
  //   });

  //   // Calculate UNREALIZED profit from current holdings
  //   const holding = holdings.find(h => h.instrumentId === instrumentId);
  //   if (holding && holding.quantity > 0) {
  //     const instrument = instruments.find(i => i.instrumentId === instrumentId);
  //     const currentPrice = instrument?.currentPrice || 0;

  //     // Calculate average cost basis of remaining holdings
  //     let costBasis = 0;
  //     let qtyToAccount = holding.quantity;

  //     // Work backwards through buys to find cost of current holdings
  //     for (let i = buys.length - 1; i >= 0 && qtyToAccount > 0; i--) {
  //       const buyQty = Math.min(qtyToAccount, buys[i].quantity);
  //       costBasis += buyQty * buys[i].executionPrice;
  //       qtyToAccount -= buyQty;
  //     }

  //     const currentValue = holding.quantity * currentPrice;
  //     totalProfit += currentValue - costBasis;
  //   }
  // });

  return totalProfit;
}