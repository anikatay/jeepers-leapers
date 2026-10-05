import { Component, OnInit, Signal, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { AccountService } from '../../../../core/services/account.service';
import { HoldingResponse } from '../../../../core/models/holding.model';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { AccountResponse } from '../../../../core/models/account.model';

/**
 * Frontend-only display interface combining holding + instrument data
 */
interface PortfolioHoldingView {
  accountId: string;
  instrumentId: string;
  ticker: string;
  name: string;
  quantity: number;
  currentPrice: number;
  currentValue: number;  // quantity * currentPrice
}

type SortKey = 'quantity' | 'ticker' | 'name';

interface SummaryCard {
  label: string;
  value: string;
  isPositive?: boolean;
}

@Component({
  selector: 'app-client-portfolio',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './client-portfolio.component.html',
  styleUrl: './client-portfolio.component.css'
})
export class ClientPortfolioComponent implements OnInit {
  // Data signals
  portfolioHoldings = signal<PortfolioHoldingView[]>([]);
  totalPortfolioValue = signal<number>(0);
  cashAvailable = signal<number>(0);
  
  // UI state signals
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  searchQuery = signal<string>('');
  filterType = signal<'all' | 'gain' | 'loss'>('all');
  sortBy = signal<SortKey>('quantity');
  sortDir = signal<'asc' | 'desc'>('desc');
  filterOptions: Array<'all' | 'gain' | 'loss'> = ['all', 'gain', 'loss'];

  private accountId = 'a1000000-0000-0000-0000-000000000001';

  constructor(
    private portfolioService: PortfolioService,
    private instrumentService: InstrumentService,
    private accountService: AccountService
  ) {}

  ngOnInit() {
    this.loadPortfolioData();
  }

  /**
   * Load holdings, instruments, and account data in parallel
   * Then join/match holdings with instruments and calculate portfolio values
   */
  private loadPortfolioData(): void {
    this.loading.set(true);
    this.error.set(null);

    // Load all three data sources in parallel
    forkJoin({
      holdings: this.portfolioService.getPortfolio(this.accountId),
      instruments: this.instrumentService.getAllInstruments(),
      account: this.accountService.getAccount(this.accountId)
    }).subscribe({
      next: (data) => {
        const { holdings, instruments, account } = data;

        // Set account balance (cash available)
        this.cashAvailable.set(account.balance);

        // Join holdings with instruments by matching instrumentId
        const instrumentMap = new Map<string, InstrumentResponse>();
        instruments.forEach(instr => {
          instrumentMap.set(instr.instrumentId, instr);
        });

        // Create PortfolioHoldingView for each holding
        const portfolioViews: PortfolioHoldingView[] = holdings.map(holding => {
          const instrument = instrumentMap.get(holding.instrumentId);
          const currentPrice = instrument?.currentPrice ?? 0;
          const currentValue = holding.quantity * currentPrice;

          return {
            accountId: holding.accountId,
            instrumentId: holding.instrumentId,
            ticker: instrument?.ticker ?? 'N/A',
            name: instrument?.name ?? 'N/A',
            quantity: holding.quantity,
            currentPrice: currentPrice,
            currentValue: currentValue
          };
        });

        // Calculate total portfolio value
        const total = portfolioViews.reduce((sum, view) => sum + view.currentValue, 0);
        this.totalPortfolioValue.set(total);

        // Set the portfolio holdings
        this.portfolioHoldings.set(portfolioViews);

        this.loading.set(false);
      },
      error: (err) => {
        this.error.set(err?.message || 'Failed to load portfolio data');
        this.loading.set(false);
      }
    });
  }

  getSummaryCards(): SummaryCard[] {
    const totalValue = this.totalPortfolioValue();
    const cash = this.cashAvailable();

    return [
      {
        label: 'Total Portfolio Value',
        value: totalValue > 0 ? `$${totalValue.toLocaleString('en-US', { maximumFractionDigits: 2 })}` : 'N/A',
        isPositive: undefined
      },
      {
        label: 'Today Gain Loss',
        value: 'N/A',
        isPositive: undefined
      },
      {
        label: 'Overall Gain Loss',
        value: 'N/A',
        isPositive: undefined
      },
      {
        label: 'Cash Available',
        value: cash > 0 ? `$${cash.toLocaleString('en-US', { maximumFractionDigits: 2 })}` : 'N/A',
        isPositive: undefined
      }
    ];
  }

  getFilteredPortfolio(): PortfolioHoldingView[] {
    const data = this.portfolioHoldings();
    if (!data) return [];

    const query = this.searchQuery().toLowerCase();
    let filtered = data.filter((holding: PortfolioHoldingView) => {
      return (
        holding.ticker.toLowerCase().includes(query) ||
        holding.name.toLowerCase().includes(query) ||
        holding.instrumentId.toLowerCase().includes(query)
      );
    });

    if (this.filterType() !== 'all') {
      // TODO: Implement gain/loss filter when buying price data available
    }

    const sortField = this.sortBy();
    filtered.sort((a: PortfolioHoldingView, b: PortfolioHoldingView) => {
      let va: any = a[sortField];
      let vb: any = b[sortField];

      if (typeof va === 'string') va = va.toLowerCase();
      if (typeof vb === 'string') vb = vb.toLowerCase();

      const result = va < vb ? -1 : va > vb ? 1 : 0;
      return this.sortDir() === 'desc' ? -result : result;
    });

    return filtered;
  }

  toggleSort(field: SortKey) {
    if (this.sortBy() === field) {
      this.sortDir.set(this.sortDir() === 'desc' ? 'asc' : 'desc');
    } else {
      this.sortBy.set(field);
      this.sortDir.set('desc');
    }
  }

  setFilter(filter: 'all' | 'gain' | 'loss'): void {
    this.filterType.set(filter);
  }

  getStockIconLabel(ticker: string): string {
    return (ticker || 'N/A').slice(0, 2).toUpperCase();
  }

  onBuy(holding: PortfolioHoldingView) {
    console.log('Buy clicked for:', holding.ticker);
    // TODO: Open buy modal when ready
  }

  onSell(holding: PortfolioHoldingView) {
    console.log('Sell clicked for:', holding.ticker);
    // TODO: Open sell modal when ready
  }
}