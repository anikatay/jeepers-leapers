import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { HistoryResponse } from "../models/market.model";

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

	getExchangeRate(baseCurrency: string, quoteCurrency: string): Observable<FxRateResponse> {
		return this.http.get<FxRateResponse>(`${this.apiUrl}/fx-rate`, {
			params: {
				base: baseCurrency,
				quote: quoteCurrency
			}
		});
	}
}