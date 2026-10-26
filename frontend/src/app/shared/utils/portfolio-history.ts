import { HistoryResponse } from '../../core/models/market.model';

export interface PortfolioHistoryPoint {
  date: string;
  value: number;
}

const MINUTE_MS = 60_000;

export function normalizeIntradayBaseline(history: HistoryResponse): HistoryResponse {
  const bars = history?.bars;
  if (!bars || bars.length < 2) return history;

  const [first, second] = bars;
  const firstTimestamp = parseBarTimestamp(first.date);
  const secondTimestamp = parseBarTimestamp(second.date);

  const isSynthetic =
    Number.isFinite(firstTimestamp) &&
    Number.isFinite(secondTimestamp) &&
    firstTimestamp % (24 * 60 * MINUTE_MS) === 0 &&
    first.volume === 0 &&
    first.open === first.close &&
    first.high === first.close &&
    first.low === first.close &&
    secondTimestamp - firstTimestamp > MINUTE_MS;

  if (!isSynthetic) return history;

  return {
    ...history,
    bars: [
      { ...first, date: new Date(secondTimestamp - MINUTE_MS).toISOString() },
      ...bars.slice(1)
    ]
  };
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

interface TimedBar {
  date: string;
  timestamp: number;
  close: number;
}

interface TickerPriceState {
  bars: TimedBar[];
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
  const intraday = histories.some(history =>
    history.bars.some(bar => bar.date.includes('T'))
  );

  histories.forEach(history => {
    history.bars.forEach(bar => {
      if (Number.isFinite(parseBarTimestamp(bar.date))) {
        dates.add(bar.date);
      }
    });

    const bars = history.bars
      .map(bar => ({
        date: bar.date,
        timestamp: parseBarTimestamp(bar.date),
        close: bar.close
      }))
      .filter(bar => Number.isFinite(bar.timestamp))
      .sort((a, b) => a.timestamp - b.timestamp);

    priceStateByTicker.set(history.ticker, {
      bars,
      index: 0,
      // Seed from the earliest known close so a ticker whose series starts later than
      // its peers is valued from the first point instead of counting as zero, which
      // otherwise ramps the total up artificially at the left edge.
      close: bars.length > 0 ? bars[0].close : null
    });
  });

  const sortedDates = Array.from(dates).sort(
    (a, b) => parseBarTimestamp(a) - parseBarTimestamp(b)
  );
  const quantitiesByTicker = new Map<string, number>();
  const tradeIndexesByTicker = new Map<string, number>();

  return sortedDates.map(date => {
    const dateTimestamp = parseBarTimestamp(date);
    const tradeCutoff = intraday
      ? dateTimestamp
      : endOfDayTimestamp(date);
    let value = 0;

    priceStateByTicker.forEach((priceState, ticker) => {
      const tickerTrades = tradesByTicker.get(ticker) ?? [];
      let tradeIndex = tradeIndexesByTicker.get(ticker) ?? 0;
      let quantity = quantitiesByTicker.get(ticker) ?? 0;

      while (
        tradeIndex < tickerTrades.length &&
        tickerTrades[tradeIndex].timestamp <= tradeCutoff
      ) {
        quantity += tickerTrades[tradeIndex].quantity;
        tradeIndex += 1;
      }

      tradeIndexesByTicker.set(ticker, tradeIndex);
      quantitiesByTicker.set(ticker, quantity);

      while (
        priceState.index < priceState.bars.length &&
        priceState.bars[priceState.index].timestamp <= dateTimestamp
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

function parseBarTimestamp(date: string): number {
  const normalized = date.length === 10 ? `${date}T00:00:00Z` : date;
  return new Date(normalized).getTime();
}

function endOfDayTimestamp(date: string): number {
  return new Date(`${date.slice(0, 10)}T23:59:59.999Z`).getTime();
}
