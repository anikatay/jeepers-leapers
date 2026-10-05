import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Trade, TradeRequest, TradeResponse } from '../models/trade.model';

@Injectable({
  providedIn: 'root'
})
export class TradesService {

  trades = signal<Trade[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  /**
   * Create a new trade (BUY or SELL)
   * @param request TradeRequest with accountId, instrumentId, side (BUY|SELL), and quantity
   * @returns Observable of the created Trade
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

  getTrades(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Trade[]>(`/api/trades`)
      .subscribe({
        next: (data) => {
          this.trades.set(data);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.loading.set(false);
        }
      });
  }
}