export interface Trade {
  tradeId: string;
  instrumentName: string;
  ticker: string;
  side: string;
  quantity: number;
  executionPrice: number;
  tradeValue: number;
  executedAt: string;
}