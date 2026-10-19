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