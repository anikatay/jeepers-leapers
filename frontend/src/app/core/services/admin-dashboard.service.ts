import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AdminAccountTrades,
  AdminDashboardResponse
} from '../models/admin-dashboard.model';
import { AccountResponse } from '../models/account.model';

@Injectable({
  providedIn: 'root'
})
export class AdminDashboardService {
  private readonly apiUrl = '/api/admin';

  constructor(private http: HttpClient) {}

  getDashboard(search: string, page: number, size: number): Observable<AdminDashboardResponse> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size);

    if (search.trim()) {
      params = params.set('search', search.trim());
    }

    return this.http.get<AdminDashboardResponse>(`${this.apiUrl}/dashboard`, { params });
  }

  getAccountTrades(accountId: string, limit = 50, offset = 0): Observable<AdminAccountTrades> {
    const params = new HttpParams()
      .set('limit', limit)
      .set('offset', offset);

    return this.http.get<AdminAccountTrades>(
      `${this.apiUrl}/accounts/${accountId}/trades`,
      { params }
    );
  }

  freezeAccount(accountId: string, reason: string): Observable<AccountResponse> {
    return this.http.patch<AccountResponse>(
      `${this.apiUrl}/accounts/${accountId}/freeze`,
      { reason }
    );
  }

  unfreezeAccount(accountId: string): Observable<AccountResponse> {
    return this.http.patch<AccountResponse>(`${this.apiUrl}/accounts/${accountId}/unfreeze`, {});
  }
}
