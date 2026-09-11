// client-portfolio.component.ts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { Holding } from '../../../../core/models/portfolio.model'; 

@Component({
  selector: 'app-client-portfolio',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './client-portfolio.component.html',
  styleUrl: './client-portfolio.component.css'
})
export class ClientPortfolioComponent implements OnInit {
  portfolio;

  // Column config — this is what makes the table "dynamic."
  // Each entry says: what to show as the header, and which field on each row to read.
  columns: { header: string; field: keyof Holding }[] = [
    { header: 'Name', field: 'instrumentName' },
    { header: 'Holding Value', field: 'holdingValue' }
  ];

  constructor(private portfolioService: PortfolioService) {
    this.portfolio = this.portfolioService.portfolio;
  }

  ngOnInit() {
    const userId = 'a1000000-0000-0000-0000-000000000001';
    this.portfolioService.getPortfolio(userId);
  }
}