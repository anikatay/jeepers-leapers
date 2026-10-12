import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ExchangeRequest,
  ExchangeResponse
} from '../models/exchange.model';

@Injectable({
  providedIn: 'root'
})
export class ExchangeService {
  private apiUrl = '/api/exchanges';

  constructor(private http: HttpClient) {}

  /**
   * Get all exchanges
   * GET /api/exchanges
   * @returns Observable<ExchangeResponse[]> with list of all exchanges
   */
  getAllExchanges(): Observable<ExchangeResponse[]> {
    return this.http.get<ExchangeResponse[]>(this.apiUrl);
  }

  /**
   * Get a specific exchange by ID
   * GET /api/exchanges/{exchangeId}
   * @param exchangeId ID of the exchange
   * @returns Observable<ExchangeResponse> with exchange details
   */
  getExchangeById(exchangeId: string): Observable<ExchangeResponse> {
    return this.http.get<ExchangeResponse>(`${this.apiUrl}/${exchangeId}`);
  }

  /**
   * Create a new exchange
   * POST /api/exchanges
   * @param request ExchangeRequest with exchange details
   * @returns Observable<ExchangeResponse> with created exchange details
   */
  createExchange(request: ExchangeRequest): Observable<ExchangeResponse> {
    return this.http.post<ExchangeResponse>(this.apiUrl, request);
  }

  /**
   * Update an existing exchange
   * PATCH /api/exchanges/{exchangeId}
   * @param exchangeId ID of the exchange to update
   * @param request ExchangeRequest with updated exchange details
   * @returns Observable<ExchangeResponse> with updated exchange details
   */
  updateExchange(exchangeId: string, request: ExchangeRequest): Observable<ExchangeResponse> {
    return this.http.patch<ExchangeResponse>(
      `${this.apiUrl}/${exchangeId}`,
      request
    );
  }

  /**
   * Delete an exchange
   * DELETE /api/exchanges/{exchangeId}
   * @param exchangeId ID of the exchange to delete
   * @returns Observable<void> - returns HTTP 204 on success
   */
  deleteExchange(exchangeId: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${exchangeId}`);
  }
}
