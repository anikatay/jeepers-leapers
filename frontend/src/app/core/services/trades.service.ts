import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TradeRequest, TradeResponse } from '../models/trade.model';

@Injectable({
  providedIn: 'root'
})
export class TradesService {

  constructor(private http: HttpClient) {}

  /**
   * Create a new trade (BUY or SELL)
   * @param request TradeRequest with accountId, instrumentId, side (BUY|SELL), and quantity
   * @returns Observable of the created TradeResponse
   */
  createTrade(request: TradeRequest): Observable<TradeResponse> {
    return this.http.post<TradeResponse>('/api/trades', request);
  }

  /**
   * Get all trades for a specific account
   * @param accountId UUID of the account
   * @returns Observable array of TradeResponse objects
   */
  getTradesByAccount(accountId: string): Observable<TradeResponse[]> {
    return this.http.get<TradeResponse[]>(`/api/trades/account/${accountId}`);
  }

  /**
   * Get all trades for a specific account and instrument
   * @param accountId UUID of the account
   * @param instrumentId UUID of the instrument
   * @returns Observable array of TradeResponse objects
   */
  getTradesByAccountAndInstrument(accountId: string, instrumentId: string): Observable<TradeResponse[]> {
    return this.http.get<TradeResponse[]>(`/api/trades/account/${accountId}/instrument/${instrumentId}`);
  }
}