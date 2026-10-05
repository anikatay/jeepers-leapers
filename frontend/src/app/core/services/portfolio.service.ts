import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HoldingResponse } from '../models/holding.model';

@Injectable({
  providedIn: 'root'
})
export class PortfolioService {

  portfolio = signal<HoldingResponse[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  getPortfolio(accountId: string): Observable<HoldingResponse[]> {
    return this.http.get<HoldingResponse[]>(`/api/holdings/${accountId}`);
  }
}