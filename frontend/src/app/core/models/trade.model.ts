// Request DTO for creating trades
export interface TradeRequest {
  accountId: string;
  instrumentId: string;
  side: 'BUY' | 'SELL';
  quantity: number;
}

// Response DTO for trade data from backend
export interface TradeResponse {
  tradeId: string;
  accountId: string;
  instrumentId: string;
  ticker: string;
  name: string;
  instrumentCurrency?: string;
  side: string;
  quantity: number;
  executionPrice: number;
  tradeValue: number;
  executedAt: string;
}

export interface TradePageFilters {
  search?: string;
  side?: string;
  currency?: string;
  fromDate?: string;
  toDate?: string;
  minQuantity?: number;
  maxQuantity?: number;
  minPrice?: number;
  maxPrice?: number;
  minTotal?: number;
  maxTotal?: number;
  sortOrder?: string;
}

// Legacy Trade interface - kept for compatibility
export interface Trade {

  clientEmail: string;
  instrumentName: string;
  tradeValue: number;

}