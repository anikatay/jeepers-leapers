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

export type QuoteStatus = 'LIVE' | 'CLOSED' | 'STALE' | 'UNAVAILABLE';

export interface MarketQuote {
	type: 'QUOTE';
	ticker: string;
	price: number;
	quoteTime: string;
	receivedTime: string;
	source: string;
	status: QuoteStatus;
}

export interface MarketSectorTicker {
	ticker: string;
	rank: number;
}

export interface MarketSector {
	id: string;
	name: string;
	tickers: MarketSectorTicker[];
}

export interface MarketSectorCatalog {
	market: string;
	selectionBasis: string;
	universeVersion: string;
	generatedAt: string;
	sectors: MarketSector[];
}

export interface MarketHeatmapTicker {
	ticker: string;
	rank: number;
	price: number | null;
	quoteTime: string | null;
	receivedTime: string | null;
	source: string | null;
	status: QuoteStatus;
}

export interface MarketHeatmapSector {
	id: string;
	name: string;
	tickers: MarketHeatmapTicker[];
}

export interface MarketHeatmapResponse {
	market: string;
	selectionBasis: string;
	universeVersion: string;
	generatedAt: string;
	asOf: string;
	sectors: MarketHeatmapSector[];
}
export interface NewsItem {
	id: string;
	title: string;
	publisher: string | null;
	link: string;
	/** ISO-8601 instant, or null when the provider gave no usable timestamp. */
	publishedAt: string | null;
	relatedTickers: string[];
}

export interface NewsResponse {
	items: NewsItem[];
}
