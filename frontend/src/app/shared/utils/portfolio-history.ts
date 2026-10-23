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

interface TickerPriceState {
  bars: HistoryResponse['bars'];
  index: number;
  close: number | null;
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

  const priceStateByTicker = new Map<string, TickerPriceState>();
  const dates = new Set<string>();
  histories.forEach(history => {
    history.bars.forEach(bar => {
      dates.add(bar.date);
    });
    priceStateByTicker.set(history.ticker, {
      bars: [...history.bars].sort((a, b) => a.date.localeCompare(b.date)),
      index: 0,
      close: null
    });
  });

  const sortedDates = Array.from(dates).sort((a, b) => a.localeCompare(b));
  const quantitiesByTicker = new Map<string, number>();
  const tradeIndexesByTicker = new Map<string, number>();

  return sortedDates.map(date => {
    const endOfDay = new Date(`${date}T23:59:59.999Z`).getTime();
    let value = 0;

    priceStateByTicker.forEach((priceState, ticker) => {
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

      while (
        priceState.index < priceState.bars.length &&
        priceState.bars[priceState.index].date <= date
      ) {
        priceState.close = priceState.bars[priceState.index].close;
        priceState.index += 1;
      }

      if (priceState.close !== null) {
        value += quantity * priceState.close;
      }
    });

    return { date, value };
  });
}
