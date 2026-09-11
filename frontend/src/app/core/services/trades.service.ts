import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Trade } from '../models/trade.model';

@Injectable({
  providedIn: 'root'
})
export class TradesService{

  trades = signal<Trade[] | null>(null);

  constructor(private http: HttpClient) {}

  getTrades(): void {
    this.http.get<Trade[]>(`/api/trades`)
      .subscribe(data => {
        this.trades.set(data);
      });
  }
}