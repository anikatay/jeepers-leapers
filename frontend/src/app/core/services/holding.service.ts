import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';
import { Holding } from '../models/holding.model';

@Injectable({
  providedIn: 'root'
})
export class HoldingService {

  holdings = signal<Holding[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  getHoldings(accountId: string) {
    this.loading.set(true);
    this.error.set(null);
    return this.http.get<Holding[]>(`/api/holdings/${accountId}`)
      .pipe(
        // Update signals on success
        tap({
          next: (data) => {
            this.holdings.set(data);
            this.loading.set(false);
          },
          error: (err) => {
            this.error.set(err.message);
            this.loading.set(false);
          }
        })
      );
  }
}