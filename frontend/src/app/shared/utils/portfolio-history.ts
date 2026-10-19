import { HistoryResponse } from '../../core/models/market.model';

export interface PortfolioHistoryPoint {
  date: string;
  value: number;
}

/**
 * Approximation: values current holdings' quantities against historical closes.
 * Does not account for quantity changes over time (past trades aren't backdated).
 */
export function combinePortfolioHistory(
  heldInstruments: Array<{ ticker: string; quantity: number }>,
  histories: HistoryResponse[]
): PortfolioHistoryPoint[] {
  const totalsByDate = new Map<string, number>();

  histories.forEach((history, idx) => {
    const quantity = heldInstruments[idx].quantity;
    history.bars.forEach(bar => {
      totalsByDate.set(bar.date, (totalsByDate.get(bar.date) ?? 0) + quantity * bar.close);
    });
  });

  return Array.from(totalsByDate.entries())
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, value]) => ({ date, value }));
    
}
