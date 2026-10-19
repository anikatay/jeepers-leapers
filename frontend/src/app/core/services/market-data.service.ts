import { Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { HistoryResponse } from "../models/market.model";

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
}