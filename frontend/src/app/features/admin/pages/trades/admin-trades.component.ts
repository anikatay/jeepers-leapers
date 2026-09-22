import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TradesService } from '../../../../core/services/trades.service';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { FormsModule } from '@angular/forms';

interface TradeWithUser {
  name: string; // User's first and last name
  tradeId: string;
  instrumentName: string;
  ticker: string;
  side: string;
  quantity: string;
  executionPrice: string;
  tradeValue: string;
  executedAt: string;
}

@Component({
  selector: 'app-admin-trades',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-trades.component.html',
  styleUrl: './admin-trades.component.css'
})
export class AdminTradesComponent {
  
}
