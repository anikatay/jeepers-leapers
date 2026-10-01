// client-portfolio.component.ts
import { Component, OnInit, Signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HoldingService } from '../../../../core/services/holding.service';
import { Holding } from '../../../../core/models/holding.model'; 

@Component({
  selector: 'app-client-portfolio',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './client-portfolio.component.html',
  styleUrl: './client-portfolio.component.css'
})
export class ClientPortfolioComponent implements OnInit {
  holding: Signal<Holding[] | null>;
  loading: Signal<boolean>;
  error: Signal<string | null>;

  // Column config — this is what makes the table "dynamic."
  // Each entry says: what to show as the header, and which field on each row to read.
  columns: { header: string; field: keyof Holding }[] = [
    { header: 'Name', field: 'quantity' },
  ];

  constructor(private holdingService: HoldingService) {
    this.holding = this.holdingService.holdings;
    this.loading = this.holdingService.loading;
    this.error = this.holdingService.error;
  }

  ngOnInit() {
    const userId = 'a1000000-0000-0000-0000-000000000001';
    this.holdingService.getHoldings(userId);
  }
}