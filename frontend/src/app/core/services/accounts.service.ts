import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Account } from '../models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AccountsService {

  trades = signal<Account[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  getTrades(userId: string): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<Account[]>(`/api/accounts/${userId}`)
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