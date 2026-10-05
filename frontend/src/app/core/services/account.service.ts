import { Injectable } from '@angular/core';
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
}
