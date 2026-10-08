import { Component, OnInit, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { AccountService } from '../../../../core/services/account.service';
import { PortfolioService } from '../../../../core/services/portfolio.service';
import { InstrumentService } from '../../../../core/services/instrument.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { TranslationService } from '../../../../core/services/translation.service';
import { TranslatePipe } from '../../../../core/pipes/translate.pipe';
import { InstrumentResponse } from '../../../../core/models/instrument.model';
import { TradeResponse } from '../../../../core/models/trade.model';


export interface StockHolding {
  symbol: string;
  name: string;
  pct: number;
  value: number;
  price: number;
}

export interface Trade {
  time: string;
  symbol: string;
  type: 'Buy' | 'Sell';
  qty: number;
  price: number;
  total: number;
}

type Period = '1D' | '1W' | '1M' | '3M' | '1Y' | 'All';

export interface PieSlice {
  symbol: string;
  percentage: number;
  angle: number;
  startAngle: number;
  color: string;
  d: string;
}

@Component({
  selector: 'app-client-home',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  templateUrl: './client-home.component.html',
  styleUrl: './client-home.component.css'
})

export class ClientHomeComponent implements OnInit {
  userInitial = 'A';
  userName = 'd4000000-0000-0000-0000-000000000001';

  // Hardcoded development account ID
  private readonly ACCOUNT_ID = 'd4000000-0000-0000-0000-000000000001';


  holdings = signal<StockHolding[]>([]);
  completedTrades = signal<Trade[]>([]);
  pieSlices = signal<PieSlice[]>([]);

  selectedPeriod = signal<Period>('1M');
  activePieIndex = signal<number | undefined>(undefined);
  periodOptions: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'All'];

  portfolioValue = signal<string>('N/A');
  todaysGain = signal<string>('N/A');
  overallGain = signal<string>('N/A');
  cashRemaining = signal<string>('N/A');

  stockIconStyles: Record<string, { label: string; background: string; color: string }> = {
    AAPL: { label: 'A', background: '#111827', color: '#ffffff' },
    TSLA: { label: 'T', background: '#dc2626', color: '#ffffff' },
    MSFT: { label: 'M', background: '#2563eb', color: '#ffffff' },
    NVDA: { label: 'N', background: '#65a30d', color: '#ffffff' },
    GOOGL: { label: 'G', background: '#f3f4f6', color: '#2563eb' },
    AMZN: { label: 'a', background: '#111827', color: '#f59e0b' },
    META: { label: 'M', background: '#2563eb', color: '#ffffff' },
    'BRK.B': { label: 'B', background: '#1e3a8a', color: '#ffffff' },
    Others: { label: 'O', background: 'var(--secondary)', color: 'var(--primary)' },
  };

  constructor(
    private router: Router,
    private accountService: AccountService,
    private portfolioService: PortfolioService,
    private instrumentService: InstrumentService,
    private tradesService: TradesService,
    private tradeModalService: TradeModalService,
    public translationService: TranslationService
  ) {
    // Reload chart data when a trade is completed
    effect(() => {
      const _tradeSignal = this.tradeModalService.tradeCompleted();
      // When tradeCompleted signal changes, reload data
      this.loadAllData();
    });
  }

  ngOnInit() {
    this.loadAllData();
  }

  private pieColors = [
    '#0ea5e9', // Cyan
    '#ec4899', // Pink
    '#f59e0b', // Amber
    '#8b5cf6', // Violet
    '#6366f1'  // Indigo
  ];

  private loadAllData() {
    // Load account info for cash remaining
    this.accountService.getAccount(this.ACCOUNT_ID).subscribe({
      next: (account) => {
        const balance = account.balance ?? 0;
        this.cashRemaining.set(
          `$${balance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
        );
      },
      error: (err) => {
        console.error('Failed to load account:', err);
        this.cashRemaining.set('N/A');
      }
    });

    // Load instruments (global list)
    this.instrumentService.getAllInstruments().subscribe({
      next: (instruments) => {
        const instrumentMap = new Map<string, InstrumentResponse>();
        instruments.forEach(inst => instrumentMap.set(inst.instrumentId, inst));
        
        // Now load holdings
        this.portfolioService.getPortfolio(this.ACCOUNT_ID).subscribe({
          next: (holdingsData) => {
            this.computeHoldingsUI(holdingsData, instrumentMap);
          },
          error: (err) => {
            console.error('Failed to load holdings:', err);
            this.holdings.set([]);
            this.pieSlices.set([]);
          }
        });
      },
      error: (err) => {
        console.error('Failed to load instruments:', err);
      }
    });

    // Load trades
    this.tradesService.getTradesByAccount(this.ACCOUNT_ID).subscribe({
      next: (trades) => {
        this.computeTradesUI(trades);
      },
      error: (err) => {
        console.error('Failed to load trades:', err);
        this.completedTrades.set([]);
      }
    });
  }

  private computeHoldingsUI(holdingsData: any[], instrumentMap: Map<string, InstrumentResponse>) {
    const computedHoldings: StockHolding[] = [];
    let totalValue = 0;

    for (const h of holdingsData) {
      // Only include holdings with positive quantity
      if (h.quantity <= 0) continue;
      
      const instrument = instrumentMap.get(h.instrumentId);
      if (instrument) {
        const value = h.quantity * (instrument.currentPrice ?? 0);
        computedHoldings.push({
          symbol: instrument.ticker,
          name: instrument.name,
          pct: 0, // Will recalculate below
          value: value,
          price: instrument.currentPrice ?? 0
        });
        totalValue += value;
      }
    }

    // Recalculate percentages
    if (totalValue > 0) {
      computedHoldings.forEach(h => {
        h.pct = Math.round((h.value / totalValue) * 100);
      });
    }

    this.holdings.set(computedHoldings);
    this.portfolioValue.set(
      `$${totalValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    );

    // Generate pie chart slices dynamically
    this.generatePieSlices(computedHoldings, totalValue);
  }

  private generatePieSlices(holdings: StockHolding[], totalValue: number) {
    const slices: PieSlice[] = [];
    let currentAngle = -90; // Start at top (negative 90 in SVG coordinates)

    holdings.forEach((holding, index) => {
      const percentage = totalValue > 0 ? (holding.value / totalValue) * 100 : 0;
      const angle = (percentage / 100) * 360; // Convert percentage to degrees

      const slice: PieSlice = {
        symbol: holding.symbol,
        percentage: Math.round(percentage),
        angle: angle,
        startAngle: currentAngle,
        color: this.pieColors[index % this.pieColors.length],
        d: this.calculatePiePath(currentAngle, angle)
      };

      slices.push(slice);
      currentAngle += angle;
    });

    this.pieSlices.set(slices);
  }

  private calculatePiePath(startAngle: number, angle: number): string {
    const radius = 90;
    const largeArc = angle > 180 ? 1 : 0;

    // Center of pie chart
    const cx = 100;
    const cy = 100;

    // Calculate start point
    const startRad = (startAngle * Math.PI) / 180;
    const x1 = cx + radius * Math.cos(startRad);
    const y1 = cy + radius * Math.sin(startRad);

    // Calculate end point
    const endRad = ((startAngle + angle) * Math.PI) / 180;
    const x2 = cx + radius * Math.cos(endRad);
    const y2 = cy + radius * Math.sin(endRad);

    // Create path: M (move to center), L (line to start), A (arc), Z (close)
    return `M ${cx} ${cy} L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArc} 1 ${x2} ${y2} Z`;
  }

  private computeTradesUI(tradeResponses: TradeResponse[]) {
    const trades: Trade[] = tradeResponses.slice(0, 4).map(t => ({
      time: t.executedAt ? new Date(t.executedAt).toLocaleTimeString() : 'N/A',
      symbol: t.ticker ?? 'N/A',
      type: t.side === 'BUY' ? 'Buy' : 'Sell',
      qty: t.quantity ?? 0,
      price: t.executionPrice ?? 0,
      total: t.tradeValue ?? 0
    }));
    this.completedTrades.set(trades);
  }

  getStockIcon(symbol: string) {
    return this.stockIconStyles[symbol] ?? {
      label: symbol.slice(0, 2).toUpperCase(),
      background: 'var(--secondary)',
      color: 'var(--primary)',
    };
  }

  setPeriod(period: Period) {
    this.selectedPeriod.set(period);
  }

  setPieActive(index: number) {
    this.activePieIndex.set(index);
  }

  clearPieActive() {
    this.activePieIndex.set(undefined);
  }

  onStockClick(holding: StockHolding) {
    if (holding.symbol !== 'Others') {
      console.log('Navigate to stock:', holding.symbol);
    }
  }

  goToPortfolio() {
    this.router.navigate(['/client/portfolio']);
  }

  goToTrades() {
    this.router.navigate(['/client/trades']);
  }

  goToProfile() {
    this.router.navigate(['/client/profile']);
  }

  handleProfileOption(option: string) {
    console.log('Profile option:', option);
  }
}
