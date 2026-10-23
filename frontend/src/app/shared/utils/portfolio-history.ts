import { HistoryResponse } from '../../core/models/market.model';

export interface PortfolioHistoryPoint {
  date: string;
  value: number;
}

export interface TradeForHistory {
  ticker: string;
  side: string;
  quantity: number;
  executedAt: string;
}

interface TimedTrade {
  timestamp: number;
  quantity: number;
}

/**
 * Replays trades through each historical bar date to value the quantities held
 * on that date rather than applying today's holdings to the entire history.
 */
export function combinePortfolioHistory(
  trades: TradeForHistory[],
  histories: HistoryResponse[]
): PortfolioHistoryPoint[] {
  const tradesByTicker = new Map<string, TimedTrade[]>();

  trades.forEach(trade => {
    const timestamp = new Date(trade.executedAt).getTime();
    if (!Number.isFinite(timestamp)) return;

    const tickerTrades = tradesByTicker.get(trade.ticker) ?? [];
    tickerTrades.push({
      timestamp,
      quantity: trade.side === 'BUY' ? trade.quantity : -trade.quantity
    });
    tradesByTicker.set(trade.ticker, tickerTrades);
  });

  tradesByTicker.forEach(tickerTrades => {
    tickerTrades.sort((a, b) => a.timestamp - b.timestamp);
  });

  const historiesByTicker = new Map<string, Map<string, number>>();
  const dates = new Set<string>();
  histories.forEach(history => {
    const barsByDate = new Map<string, number>();
    history.bars.forEach(bar => {
      dates.add(bar.date);
      barsByDate.set(bar.date, bar.close);
    });
    historiesByTicker.set(history.ticker, barsByDate);
  });

  const sortedDates = Array.from(dates).sort((a, b) => a.localeCompare(b));
  const quantitiesByTicker = new Map<string, number>();
  const tradeIndexesByTicker = new Map<string, number>();

  return sortedDates.map(date => {
    const endOfDay = new Date(`${date}T23:59:59.999Z`).getTime();
    let value = 0;

    historiesByTicker.forEach((barsByDate, ticker) => {
      const tickerTrades = tradesByTicker.get(ticker) ?? [];
      let tradeIndex = tradeIndexesByTicker.get(ticker) ?? 0;
      let quantity = quantitiesByTicker.get(ticker) ?? 0;

      while (
        tradeIndex < tickerTrades.length &&
        tickerTrades[tradeIndex].timestamp <= endOfDay
      ) {
        quantity += tickerTrades[tradeIndex].quantity;
        tradeIndex += 1;
      }

      tradeIndexesByTicker.set(ticker, tradeIndex);
      quantitiesByTicker.set(ticker, quantity);

      const close = barsByDate.get(date);
      if (close !== undefined) {
        value += quantity * close;
      }
    });

    return { date, value };
  });
}
