import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Portfolio } from '../models/portfolio.model';

@Injectable({
  providedIn: 'root'
})
export class PortfolioService {

  portfolio = signal<Portfolio | null>(null);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  getPortfolio(userId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<Portfolio>(`/api/portfolio/${userId}`)
      .subscribe({
        next: (data) => {
          this.portfolio.set(data);
          this.loading.set(false);
        },
        error: (err) => {
          this.error.set(err.message);
          this.loading.set(false);
        }
      });
  }
}