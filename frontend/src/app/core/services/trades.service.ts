import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Trade } from '../models/trade.model';

@Injectable({
  providedIn: 'root'
})
export class TradesService{

  trades = signal<Trade[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

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