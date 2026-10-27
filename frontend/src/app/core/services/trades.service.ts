import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TradePageFilters, TradeRequest, TradeResponse } from '../models/trade.model';
import { PaginatedTradeResponse } from '../models/admin-dashboard.model';
import { HoldingService } from '../services/holding.service';
import { InstrumentService } from '../services/instrument.service';
import { calculateCurrentPortfolioValue, calculateAccountProfit } from '../../shared/utils/portfolio-calculation';

@Injectable({
  providedIn: 'root'
})
export class TradesService {

  portfolioMetrics = signal<{ currentValue: number, allTimeProfit: number } | null>(null);
  trades = signal<TradeResponse[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);


  constructor(
    private holdingService: HoldingService,
    private instrumentService: InstrumentService,
    private http: HttpClient) {}

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

  getTradesPage(
    accountId: string,
    page = 0,
    size = 10,
    filters: TradePageFilters = {}
  ): Observable<PaginatedTradeResponse> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    const filterValues: Record<string, string | number | undefined> = {
      search: filters.search?.trim() || undefined,
      side: filters.side,
      currency: filters.currency,
      fromDate: filters.fromDate,
      toDate: filters.toDate,
      minQuantity: filters.minQuantity,
      maxQuantity: filters.maxQuantity,
      minPrice: filters.minPrice,
      maxPrice: filters.maxPrice,
      minTotal: filters.minTotal,
      maxTotal: filters.maxTotal,
      sortOrder: filters.sortOrder
    };

    Object.entries(filterValues).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        params = params.set(key, value);
      }
    });

    return this.http.get<PaginatedTradeResponse>(`/api/trades/account/${accountId}/page`, { params });
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

  getTrades(accountId: string): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<TradeResponse[]>(`api/trades/account/${accountId}`)
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

  calculatePortfolioMetrics(accountId: string): void {
    // Gather all data
    this.http.get<TradeResponse[]>(`api/trades/account/${accountId}`).subscribe(trades => {
      this.holdingService.getHoldings(accountId).subscribe(holdings => {
        this.instrumentService.getInstruments().subscribe(instruments => {
          // Call pure functions with actual data
          const metrics = {
            currentValue: calculateCurrentPortfolioValue(holdings, instruments),
            allTimeProfit: calculateAccountProfit(holdings, instruments, trades)
          };
          this.portfolioMetrics.set(metrics);
        });
      });
    });
  }
}          