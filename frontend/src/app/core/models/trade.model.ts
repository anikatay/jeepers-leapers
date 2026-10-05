export interface Trade {
  tradeId: string;
  accountId: string;
  instrumentId: string;
  ticker: string;
  side: string;
  quantity: number;
  executionPrice: number;
  tradeValue: number;
  executedAt: string;
  instrumentName: string;
}