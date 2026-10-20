export interface PriceBar{
	date: string;
	open: number;
	high: number;
	low: number;
	close: number;
	volume: number;
}

export interface HistoryResponse{
	ticker: string;
	period: string;
	bars: PriceBar[];
}

export type QuoteStatus = 'LIVE' | 'CLOSED' | 'STALE';

export interface MarketQuote {
	type: 'QUOTE';
	ticker: string;
	price: number;
	quoteTime: string;
	receivedTime: string;
	source: string;
	status: QuoteStatus;
}