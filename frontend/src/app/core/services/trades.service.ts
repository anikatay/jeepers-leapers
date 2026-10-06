import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Trade } from '../models/trade.model';
import { HoldingService } from '../services/holding.service';
import { InstrumentService } from '../services/instrument.service';
import { calculateCurrentPortfolioValue, calculateAllTimePortfolioValue, calculateAccountProfit } from '../../shared/utils/portfolio-calculation';

@Injectable({
  providedIn: 'root'
})
export class TradesService {
  portfolioMetrics = signal<{ currentValue: number, allTimeValue: number, allTimeProfit: number } | null>(null);
  trades = signal<Trade[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(
    private holdingService: HoldingService,
    private instrumentService: InstrumentService,
    private http: HttpClient) {}

  getTrades(accountId: string): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<Trade[]>(`api/trades/account/${accountId}`)
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
    this.http.get<Trade[]>(`api/trades/account/${accountId}`).subscribe(trades => {
      this.holdingService.getHoldings(accountId).subscribe(holdings => {
        this.instrumentService.getInstruments().subscribe(instruments => {
          // Call pure functions with actual data
          const metrics = {
            currentValue: calculateCurrentPortfolioValue(holdings, instruments),
            allTimeValue: calculateAllTimePortfolioValue(holdings, instruments, trades),
            allTimeProfit: calculateAccountProfit(holdings, instruments, trades)
          };
          this.portfolioMetrics.set(metrics);
        });
      });
    });
  }
}          