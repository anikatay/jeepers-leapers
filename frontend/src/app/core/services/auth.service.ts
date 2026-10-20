import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, switchMap, tap, throwError } from 'rxjs';
import { AuthResponse, AuthRequest, RegisterRequest } from '../models/auth.model';
import { UsersService } from './users.service';


@Injectable({
  providedIn: 'root'
})
export class AuthService{
  private readonly TOKEN_KEY = 'token';
  private readonly SESSION_KEY = 'session';

  private _session = signal<AuthResponse | null>(this.loadSession());
  session = this._session.asReadonly();

  constructor(private http: HttpClient, private usersService: UsersService) {}

  /**
   * Create a new trade (BUY or SELL)
   * @param request TradeRequest with accountId, instrumentId, side (BUY|SELL), and quantity
   * @returns Observable of the created TradeResponse
   */

  login(request: AuthRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', request).pipe(
      switchMap(res => {
        localStorage.setItem(this.TOKEN_KEY, res.token);

        return this.usersService.getUserById(res.userId).pipe(
          map(user => ({
            ...res,
            firstName: user.firstName,
            lastName: user.lastName
          }))
        );
      }),
      tap(res => {
        localStorage.setItem(this.SESSION_KEY, JSON.stringify(res));
        this._session.set(res);
      }),
      catchError(error => {
        this.clearSession();
        return throwError(() => error);
      })
    );
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', request).pipe(
      tap(res => this.saveSession(res))
    );
  }

  get token() { return localStorage.getItem(this.TOKEN_KEY); }
  get userId() { return this._session()?.userId ?? null; }
  get role() { return this._session()?.role ?? null; }
  get accountId() { return this._session()?.accountId ?? null; }
  get userName(): string {
    return this._session()?.firstName?.trim() || 'User';
  }
  isLoggedIn() { return !!this.token; }

  private loadSession(): AuthResponse | null {
    try {
      const raw = localStorage.getItem(this.SESSION_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }

  private saveSession(response: AuthResponse) {
    localStorage.setItem(this.TOKEN_KEY, response.token);
    localStorage.setItem(this.SESSION_KEY, JSON.stringify(response));
    this._session.set(response);
  }

  logout() {
    this.clearSession();
  }

  private clearSession() {
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.SESSION_KEY);
    this._session.set(null);
  }
}