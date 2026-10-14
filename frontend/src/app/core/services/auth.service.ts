import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthResponse, AuthRequest } from '../models/auth.model';


@Injectable({
  providedIn: 'root'
})
export class AuthService{
      constructor(private http: HttpClient) {}

      /**
       * Create a new trade (BUY or SELL)
       * @param request TradeRequest with accountId, instrumentId, side (BUY|SELL), and quantity
       * @returns Observable of the created TradeResponse
       */
      createTrade(request: AuthRequest): Observable<AuthResponse> {
        return this.http.post<AuthResponse>('/api/trades', request);
      }
}