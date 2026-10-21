import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import {
	HistoryResponse,
	MarketHeatmapResponse,
	MarketQuote,
	MarketSector,
	MarketSectorCatalog
} from "../models/market.model";

export interface FxRateResponse {
	baseCurrency: string;
	quoteCurrency: string;
	rate: number;
	quoteTime: string;
}

@Injectable({
	providedIn: 'root'
})

export class MarketDataService{
	private apiUrl = '/api/market';

	constructor(private http: HttpClient){}

	getHistory(ticker: string, period: string = '1y'): Observable<HistoryResponse>{
		return this.http.get<HistoryResponse>(`${this.apiUrl}/${ticker}/history`, {
			params: {period}
		});
	}

	getQuote(ticker: string): Observable<MarketQuote> {
		return this.http.get<MarketQuote>(`${this.apiUrl}/${ticker}/quote`);
	}

	getSectorCatalog(): Observable<MarketSectorCatalog> {
		return this.http.get<MarketSectorCatalog>(`${this.apiUrl}/sectors`);
	}

	getSector(sectorId: string): Observable<MarketSector> {
		return this.http.get<MarketSector>(`${this.apiUrl}/sectors/${sectorId}`);
	}

	getHeatmap(sectorId?: string): Observable<MarketHeatmapResponse> {
		let params = new HttpParams();
		if (sectorId) {
			params = params.set('sector', sectorId);
		}
		return this.http.get<MarketHeatmapResponse>(`${this.apiUrl}/heatmap`, { params });
	}

	getExchangeRate(baseCurrency: string, quoteCurrency: string): Observable<FxRateResponse> {
		return this.http.get<FxRateResponse>(`${this.apiUrl}/fx-rate`, {
			params: {
				base: baseCurrency,
				quote: quoteCurrency
			}
		});
	}
}