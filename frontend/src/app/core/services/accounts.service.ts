import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Account } from '../models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AccountService {

  accounts = signal<Account[] | null>(null);
  allAccounts = signal<Account[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  getAccounts(userId: string): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<Account[]>(`/api/accounts/${userId}`)
      .subscribe({
        next: (data) => {
          this.accounts.set(data);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.loading.set(false);
        }
      });
  }

  getAllAccounts(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<Account[]>(`/api/accounts`)
      .subscribe({
        next: (data) => {
          this.allAccounts.set(data);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.loading.set(false);
        }
      });
  }
}