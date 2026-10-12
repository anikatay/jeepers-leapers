import { Observable } from 'rxjs';
import {
  InstrumentRequest,
  InstrumentResponse
} from '../models/instrument.model';
import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class InstrumentService {

  private apiUrl = '/api/instruments';

  constructor(private http: HttpClient) {}

  /**
   * Get all instruments
   * GET /api/instruments
   * @returns Observable<InstrumentResponse[]> with list of all instruments
   */
  getAllInstruments(): Observable<InstrumentResponse[]> {
    return this.http.get<InstrumentResponse[]>(this.apiUrl);
  }

  /**
   * Get a specific instrument by ticker
   * GET /api/instruments/{ticker}
   * @param ticker Stock ticker symbol
   * @returns Observable<InstrumentResponse> with instrument details
   */
  getInstrumentByTicker(ticker: string): Observable<InstrumentResponse> {
    return this.http.get<InstrumentResponse>(`${this.apiUrl}/${ticker}`);
  }

  /**
   * Create a new instrument
   * POST /api/instruments
   * @param request InstrumentRequest with instrument details
   * @returns Observable<InstrumentResponse> with created instrument details
   */
  createInstrument(request: InstrumentRequest): Observable<InstrumentResponse> {
    return this.http.post<InstrumentResponse>(this.apiUrl, request);
  }

  /**
   * Update instrument price
   * PATCH /api/instruments/{instrumentId}/price
   * @param instrumentId UUID of the instrument to update
   * @param price New price for the instrument
   * @returns Observable<void> - returns HTTP 204 on success
   */
  updateInstrumentPrice(instrumentId: string, price: number): Observable<void> {
    return this.http.patch<void>(
      `${this.apiUrl}/${instrumentId}/price`,
      null,
      { params: { price: price.toString() } }
    );
  }


  instruments = signal<InstrumentResponse[] | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);


  getInstruments() {
    this.loading.set(true);
    this.error.set(null);
    return this.http.get<InstrumentResponse[]>(`/api/instruments`)
      .pipe(
        // Update signals on success
        tap({
          next: (data) => {
            this.instruments.set(data);
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