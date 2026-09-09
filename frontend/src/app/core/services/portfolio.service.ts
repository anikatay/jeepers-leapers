import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Portfolio } from '../models/portfolio.model';

@Injectable({
  providedIn: 'root'
})
export class PortfolioService {

  portfolio = signal<Portfolio | null>(null);

  constructor(private http: HttpClient) {}

  getPortfolio(userId: string): void {
    this.http.get<Portfolio>(`/api/portfolio/${userId}`)
      .subscribe(data => {
        this.portfolio.set(data);
      });
  }
}