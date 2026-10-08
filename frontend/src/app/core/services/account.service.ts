import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AccountResponse } from '../models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AccountService {
  private apiUrl = '/api/accounts';

  constructor(private http: HttpClient) {}

  /**
   * Get account details by accountId
   * GET /api/accounts/{accountId}
   * @param accountId UUID of the account
   * @returns Observable<AccountResponse> with account details including balance
   */
  getAccount(accountId: string): Observable<AccountResponse> {
    return this.http.get<AccountResponse>(`${this.apiUrl}/${accountId}`);
  }
   accounts = signal<AccountResponse[] | null>(null);
  allAccounts = signal<AccountResponse[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  getAccounts(userId: string): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<AccountResponse[]>(`/api/accounts/${userId}`)
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
    this.http.get<AccountResponse[]>(`/api/accounts/active`)
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
