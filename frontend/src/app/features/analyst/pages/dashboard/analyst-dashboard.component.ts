import { CommonModule } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslationService } from '../../../../core/services/translation.service';
import {
  AnalystDataSource,
  AnalystInstrument,
  AnalystMockDataSource,
  AnalystTrade
} from '../../data-access/analyst-data-source';

type Period = '1D' | '1W' | '1M' | '3M' | '1Y' | 'ALL';
type Metric = 'Both' | 'Buy' | 'Sell' | 'Profit/Loss';
type ChartType = 'Line' | 'Bar' | 'Candlestick';
type Scope = 'All Users' | 'Specific User' | 'Specific Account';
type SortOrder = 'Newest' | 'Oldest' | 'Highest value' | 'Lowest value';
type VolumeMode = 'Buy' | 'Sell' | 'Both' | 'Net Flow';
type VolumeChartType = 'Area' | 'Line' | 'Bar' | 'Stacked Bar';
type VolumeGrouping = 'Hourly' | 'Daily' | 'Weekly' | 'Monthly';
type VolumePeriod = Period | 'Custom';

interface ChartRow {
  date: string;
  label: string;
  buy: number;
  sell: number;
  total: number;
  profitLoss: number;
  open: number;
  high: number;
  low: number;
  close: number;
}

interface ChartSeries {
  symbol: string;
  color: string;
  rows: ChartRow[];
}

interface VolumeRow {
  label: string;
  date: string;
  buy: number;
  sell: number;
  total: number;
  net: number;
  trades: number;
}

@Component({
  selector: 'app-analyst-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [
    { provide: AnalystDataSource, useClass: AnalystMockDataSource }
  ],
  templateUrl: './analyst-dashboard.component.html',
  styleUrl: './analyst-dashboard.component.css'
})
export class AnalystDashboardComponent {
  private readonly axisFormatter = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1
  });

  private readonly seriesPalette = ['#0ea5e9', '#f59e0b', '#8b5cf6', '#64748b'];
  readonly gridLevels = [0, 25, 50, 75];
  readonly periods: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'ALL'];
  readonly metrics: Metric[] = ['Both', 'Buy', 'Sell', 'Profit/Loss'];
  readonly chartTypes: ChartType[] = ['Line', 'Bar', 'Candlestick'];
  readonly scopes: Scope[] = ['All Users', 'Specific User', 'Specific Account'];

  period = signal<Period>('3M');
  metric = signal<Metric>('Both');
  chartType = signal<ChartType>('Line');
  scope = signal<Scope>('All Users');
  scopeSearch = signal('');
  comparisonOpen = signal(false);
  comparisonSearch = signal('');
  percentage = signal(false);
  hoverIndex = signal<number | null>(null);
  selectedStock = signal<string | null>(null);
  search = signal('');
  activityDate = signal('');
  sideFilter = signal('All');
  instrumentFilter = signal('All');
  sortOrder = signal<SortOrder>('Newest');
  selectedSymbols = signal<string[]>([]);
  trades = signal<AnalystTrade[]>([]);
  instruments = signal<AnalystInstrument[]>([]);
  loading = signal(true);
  error = signal(false);
  selectedTrade = signal<AnalystTrade | null>(null);
  volumeMode = signal<VolumeMode>('Both');
  volumeChartType = signal<VolumeChartType>('Stacked Bar');
  volumeGrouping = signal<VolumeGrouping>('Weekly');
  volumeStock = signal('All');
  volumeUser = signal('All');
  volumeAccount = signal('All');
  volumePeriod = signal<VolumePeriod>('3M');
  volumeStartDate = signal('2026-09-01');
  volumeEndDate = signal('2026-10-16');
  readonly volumeRangeMin = '2026-09-01';
  readonly volumeRangeMax = '2026-10-16';
  volumeTooltip = signal<VolumeRow | null>(null);
  selectedVolumePoint = signal<VolumeRow | null>(null);

  filteredTrades = computed(() => {
    const cutoff = this.periodCutoff();
    const search = this.search().trim().toLowerCase();
    return this.trades()
      .filter(trade => new Date(trade.executedAt).getTime() >= cutoff)
      .filter(trade => this.sideFilter() === 'All' || trade.side.toUpperCase() === this.sideFilter())
      .filter(trade => this.instrumentFilter() === 'All' || trade.ticker === this.instrumentFilter())
      .filter(trade => !search || `${trade.ticker} ${trade.name} ${trade.accountId}`.toLowerCase().includes(search))
      .sort((left, right) => this.compareTrades(left, right));
  });

  activityTrades = computed(() => this.filteredTrades()
    .filter(trade => !this.activityDate() || trade.executedAt.slice(0, 10) === this.activityDate()));

  chartRows = computed<ChartRow[]>(() => {
    const trades = this.filteredTrades().filter(trade => this.selectedSymbols().includes(trade.ticker) && this.matchesScope(trade));
    return this.groupRows(trades).slice(-12);
  });

  chartSeries = computed<ChartSeries[]>(() => {
    const dates = this.chartRows().map(row => row.date);
    const scoped = this.filteredTrades().filter(trade => this.matchesScope(trade));
    return this.selectedSymbols().map((symbol, index) => {
      const byDate = new Map(this.groupRows(scoped.filter(trade => trade.ticker === symbol)).map((row): [string, ChartRow] => [row.date, row]));
      return {
        symbol,
        color: this.seriesPalette[index % this.seriesPalette.length],
        rows: dates.map(date => byDate.get(date) ?? this.emptyRow(date))
      };
    });
  });

  candleRows = computed<ChartRow[]>(() => {
    const rows = this.chartSeries()[0]?.rows ?? [];
    // Mock data has no intraday history, so each candle opens at the previous close.
    let previousClose = rows.find(row => row.total > 0)?.open ?? 0;
    return rows.map(row => {
      if (row.total <= 0) return row;
      const open = previousClose;
      const close = row.close;
      previousClose = close;
      return { ...row, open, close, high: Math.max(row.high, open, close), low: Math.min(row.low, open, close) };
    });
  });

  legendSeries = computed(() => this.chartType() === 'Candlestick' ? this.chartSeries().slice(0, 1) : this.chartSeries());

  isPercent = computed(() => this.percentage() && this.metric() === 'Both' && this.chartType() !== 'Candlestick');

  headline = computed(() => {
    const values = this.chartRows().map(row => this.metricValue(row));
    if (!values.length) return null;
    const sum = values.reduce((total, value) => total + value, 0);
    const first = values[0];
    const change = first ? ((values[values.length - 1] - first) / Math.abs(first)) * 100 : 0;
    return {
      value: this.isPercent() ? sum / values.length : sum,
      change: Math.abs(change),
      up: change >= 0
    };
  });

  hoveredPoint = computed(() => {
    const index = this.hoverIndex();
    return index === null ? null : this.chartRows()[index] ?? null;
  });

  hoveredCandle = computed(() => {
    const index = this.hoverIndex();
    const row = index === null ? undefined : this.candleRows()[index];
    return row && row.total > 0 ? row : null;
  });

  hoveredSeries = computed(() => {
    const index = this.hoverIndex();
    if (index === null) return [];
    return this.chartSeries().map(series => {
      const value = this.metricValue(series.rows[index]);
      return { symbol: series.symbol, color: series.color, value, x: this.chartX(index), y: this.yPercent(value) };
    });
  });

  tooltipFlip = computed(() => (this.hoverIndex() ?? 0) > this.chartRows().length / 2);

  endTags = computed(() => this.chartSeries().filter(series => series.rows.length).map(series => {
    const value = this.lastValue(series);
    return { symbol: series.symbol, color: series.color, y: Math.min(97, Math.max(3, this.yPercent(value))), label: this.formatAxis(value) };
  }));

  volumeRows = computed<VolumeRow[]>(() => {
    const filtered = this.trades().filter(trade =>
      (this.volumeStock() === 'All' || trade.ticker === this.volumeStock()) &&
      (this.volumeUser() === 'All' || this.volumeUser() === this.volumeUserForTrade(trade)) &&
      (this.volumeAccount() === 'All' || trade.accountId === this.volumeAccount()) &&
      this.inVolumeDateRange(trade)
    );
    const groups = new Map<string, VolumeRow>();
    for (const trade of filtered) {
      const pointDate = new Date(trade.executedAt);
      const key = this.volumeGroupKey(pointDate);
      const row = groups.get(key) ?? { label: this.volumeLabel(pointDate), date: pointDate.toISOString().slice(0, 10), buy: 0, sell: 0, total: 0, net: 0, trades: 0 };
      const value = Number(trade.tradeValue) || 0;
      if (trade.side === 'BUY') row.buy += value;
      else row.sell += value;
      row.total += value;
      row.net = row.buy - row.sell;
      row.trades += 1;
      groups.set(key, row);
    }
    return [...groups.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([, row]) => row).slice(-16);
  });

  totalVolume = computed(() => this.filteredTrades().reduce((sum, trade) => sum + (Number(trade.tradeValue) || 0), 0));
  buyCount = computed(() => this.filteredTrades().filter(trade => trade.side.toUpperCase() === 'BUY').length);
  sellCount = computed(() => this.filteredTrades().filter(trade => trade.side.toUpperCase() === 'SELL').length);
  valueDomain = computed(() => {
    const values = this.chartType() === 'Line'
      ? this.chartSeries().flatMap(series => series.rows.map(row => this.metricValue(row)))
      : this.chartRows().map(row => this.metricValue(row));
    const low = Math.min(0, ...values);
    const high = Math.max(0, ...values);
    return { low, high: high > low ? high : low + 1 };
  });

  priceDomain = computed(() => {
    const rows = this.candleRows().filter(row => row.total > 0);
    if (!rows.length) return { low: 0, high: 1 };
    const low = Math.min(...rows.map(row => row.low));
    const high = Math.max(...rows.map(row => row.high));
    const pad = Math.max((high - low) * 0.08, 1);
    return { low: low - pad, high: high + pad };
  });

  chartDomain = computed(() => this.chartType() === 'Candlestick' ? this.priceDomain() : this.valueDomain());

  axisLabels = computed(() => {
    const { low, high } = this.chartDomain();
    return [0, 1, 2, 3, 4].map(step => ({
      top: step * 25,
      text: this.formatAxis(high - ((high - low) / 4) * step)
    }));
  });
  volumeMax = computed(() => Math.max(...this.volumeRows().map(row => this.volumeValue(row)), 1));

  constructor(
    private dataSource: AnalystDataSource,
    private translationService: TranslationService
  ) {
    this.dataSource.load().subscribe({
      next: snapshot => {
        this.instruments.set(snapshot.instruments);
        this.trades.set(snapshot.trades);
        this.selectedSymbols.set(snapshot.instruments.slice(0, 2).map(instrument => instrument.ticker));
        this.loading.set(false);
      },
      error: () => {
        this.error.set(true);
        this.loading.set(false);
      }
    });
  }

  text(key: string, fallback: string): string {
    return this.translationService.translate(key, fallback);
  }

  toggleSymbol(symbol: string): void {
    const selected = this.selectedSymbols();
    this.selectedSymbols.set(selected.includes(symbol)
      ? selected.filter(item => item !== symbol)
      : [...selected, symbol].slice(-4));
  }

  isSelected(symbol: string): boolean {
    return this.selectedSymbols().includes(symbol);
  }

  openStockDetails(symbol: string): void {
    this.selectedStock.set(symbol);
  }

  stockDetails(): AnalystInstrument | undefined {
    return this.instruments().find(instrument => instrument.ticker === this.selectedStock());
  }

  stockTrades(): AnalystTrade[] {
    return this.trades().filter(trade => trade.ticker === this.selectedStock()).slice(0, 4);
  }

  stockBuyTotal(): number {
    return this.stockTrades().filter(trade => trade.side === 'BUY').reduce((total, trade) => total + trade.tradeValue, 0);
  }

  stockSellTotal(): number {
    return this.stockTrades().filter(trade => trade.side === 'SELL').reduce((total, trade) => total + trade.tradeValue, 0);
  }

  tradeProfitLoss(trade: AnalystTrade): number {
    return trade.side === 'SELL' ? trade.tradeValue * 0.09 : -trade.tradeValue * 0.015;
  }

  metricValue(row: ChartRow): number {
    if (this.metric() === 'Buy') return row.buy;
    if (this.metric() === 'Sell') return row.sell;
    if (this.metric() === 'Profit/Loss') return row.profitLoss;
    if (this.percentage()) return row.total ? (row.profitLoss / row.total) * 100 : 0;
    return row.total;
  }

  comparisonInstruments(): AnalystInstrument[] {
    const query = this.comparisonSearch().trim().toLowerCase();
    return this.instruments().filter(instrument =>
      `${instrument.ticker} ${instrument.name}`.toLowerCase().includes(query)
    );
  }

  removeSymbol(symbol: string): void {
    this.selectedSymbols.set(this.selectedSymbols().filter(item => item !== symbol));
  }

  scopeOptions(): string[] {
    const values = this.trades().map(trade => this.scope() === 'Specific User' ? trade.accountId.replace('ANALYST-', 'Analyst ') : trade.accountId);
    return [...new Set(values)];
  }

  yPercent(value: number): number {
    const { low, high } = this.chartDomain();
    return 100 - ((value - low) / (high - low)) * 100;
  }

  candleTop(row: ChartRow): number {
    return Math.min(this.yPercent(row.open), this.yPercent(row.close));
  }

  candleHeight(row: ChartRow): number {
    return Math.max(1.5, Math.abs(this.yPercent(row.open) - this.yPercent(row.close)));
  }

  candleWidth(): number {
    return Math.min(4.2, (100 / Math.max(this.chartRows().length, 1)) * 0.5);
  }

  private matchesScope(trade: AnalystTrade): boolean {
    if (this.scope() === 'All Users' || !this.scopeSearch().trim()) return true;
    const query = this.scopeSearch().trim().toLowerCase();
    const value = this.scope() === 'Specific User' ? trade.accountId.replace('ANALYST-', 'Analyst ') : trade.accountId;
    return value.toLowerCase().includes(query);
  }

  linePoints(series: ChartSeries): string {
    return series.rows
      .map((row, index) => `${this.chartX(index)},${this.yPercent(this.metricValue(row))}`)
      .join(' ');
  }

  areaPoints(series: ChartSeries): string {
    if (!series.rows.length) return '';
    return `${this.chartX(0)},100 ${this.linePoints(series)} ${this.chartX(series.rows.length - 1)},100`;
  }

  onPlotMove(event: MouseEvent): void {
    const count = this.chartRows().length;
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!count || !rect.width) return;
    const index = Math.floor(((event.clientX - rect.left) / rect.width) * count);
    this.hoverIndex.set(Math.min(count - 1, Math.max(0, index)));
  }

  seriesColor(symbol: string): string {
    const index = Math.max(0, this.selectedSymbols().indexOf(symbol));
    return this.seriesPalette[index % this.seriesPalette.length];
  }

  formatAxis(value: number): string {
    if (this.isPercent()) return `${value.toFixed(1)}%`;
    return this.axisFormatter.format(value);
  }

  lastValue(series: ChartSeries): number {
    const row = series.rows[series.rows.length - 1];
    return row ? this.metricValue(row) : 0;
  }

  private groupRows(trades: AnalystTrade[]): ChartRow[] {
    const groups = new Map<string, ChartRow>();
    const chronological = [...trades].sort((left, right) => left.executedAt.localeCompare(right.executedAt));
    for (const trade of chronological) {
      const date = new Date(trade.executedAt).toISOString().slice(0, 10);
      const price = Number(trade.executionPrice) || 0;
      const value = Number(trade.tradeValue) || 0;
      const isBuy = trade.side.toUpperCase() === 'BUY';
      const row = groups.get(date) ?? { ...this.emptyRow(date), open: price, high: price, low: price };
      if (isBuy) row.buy += value;
      else row.sell += value;
      row.total += value;
      row.profitLoss += isBuy ? -value * 0.015 : value * 0.09;
      row.high = Math.max(row.high, price);
      row.low = Math.min(row.low, price);
      row.close = price;
      groups.set(date, row);
    }
    return [...groups.values()].map(row => ({
      ...row,
      high: Math.max(row.high, row.open, row.close),
      low: Math.min(row.low, row.open, row.close)
    }));
  }

  private emptyRow(date: string): ChartRow {
    return { date, label: date.slice(5), buy: 0, sell: 0, total: 0, profitLoss: 0, open: 0, high: 0, low: 0, close: 0 };
  }

  chartX(index: number): number {
    return ((index + 0.5) / this.chartRows().length) * 100;
  }

  barHeight(value: number): number {
    return Math.max(4, (value / this.valueDomain().high) * 100);
  }

  volumeValue(row: VolumeRow): number {
    if (this.volumeMode() === 'Buy') return row.buy;
    if (this.volumeMode() === 'Sell') return row.sell;
    if (this.volumeMode() === 'Net Flow') return Math.abs(row.net);
    return row.total;
  }

  volumeHeight(value: number): number {
    return Math.max(3, (Math.abs(value) / this.volumeMax()) * 100);
  }

  volumeX(index: number): number {
    const rows = this.volumeRows();
    return rows.length === 1 ? 50 : (index / (rows.length - 1)) * 100;
  }

  volumeY(row: VolumeRow): number {
    return 94 - (this.volumeValue(row) / this.volumeMax()) * 78;
  }

  volumeLinePoints(): string {
    return this.volumeRows().map((row, index) => `${this.volumeX(index)},${94 - (this.volumeValue(row) / this.volumeMax()) * 78}`).join(' ');
  }

  volumeAreaPoints(): string {
    const rows = this.volumeRows();
    return `0,94 ${this.volumeLinePoints()} 100,94`;
  }

  volumeRangeValue(date: string): number {
    const min = Date.parse(`${this.volumeRangeMin}T00:00:00Z`);
    const value = Date.parse(`${date}T00:00:00Z`);
    return Math.max(0, Math.min(this.volumeRangeDays(), Math.round((value - min) / 86400000)));
  }

  volumeRangeDays(): number {
    const min = Date.parse(`${this.volumeRangeMin}T00:00:00Z`);
    const max = Date.parse(`${this.volumeRangeMax}T00:00:00Z`);
    return Math.round((max - min) / 86400000);
  }

  setVolumeStartFromRange(value: string): void {
    const date = this.volumeDateFromRangeValue(value);
    this.volumeStartDate.set(date);
    if (date > this.volumeEndDate()) this.volumeEndDate.set(date);
  }

  setVolumeEndFromRange(value: string): void {
    const date = this.volumeDateFromRangeValue(value);
    this.volumeEndDate.set(date);
    if (date < this.volumeStartDate()) this.volumeStartDate.set(date);
  }

  private volumeDateFromRangeValue(value: string): string {
    const min = Date.parse(`${this.volumeRangeMin}T00:00:00Z`);
    const date = new Date(min + Number(value) * 86400000);
    return date.toISOString().slice(0, 10);
  }

  volumeUsers(): string[] {
    return [...new Set(this.trades().map(trade => this.volumeUserForTrade(trade)))];
  }

  volumeAccounts(): string[] {
    return [...new Set(this.trades().map(trade => trade.accountId))];
  }

  volumeUserForTrade(trade: AnalystTrade): string {
    return trade.accountId.replace('ANALYST-', 'Analyst ');
  }

  resetVolumeFilters(): void {
    this.volumeMode.set('Both');
    this.volumeChartType.set('Stacked Bar');
    this.volumeGrouping.set('Weekly');
    this.volumeStock.set('All');
    this.volumeUser.set('All');
    this.volumeAccount.set('All');
    this.volumePeriod.set('3M');
    this.volumeStartDate.set('2026-09-01');
    this.volumeEndDate.set('2026-10-16');
  }

  downloadVolumeCsv(): void {
    const rows = this.volumeRows();
    const csv = ['Date,Buy Value,Sell Value,Total Traded,Net Flow,Total Trades', ...rows.map(row => `${row.date},${row.buy},${row.sell},${row.total},${row.net},${row.trades}`)].join('\n');
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }));
    link.download = 'analyst-volume-trends.csv';
    link.click();
    URL.revokeObjectURL(link.href);
  }

  private inVolumeDateRange(trade: AnalystTrade): boolean {
    const date = trade.executedAt.slice(0, 10);
    const periodDays: Record<Exclude<Period, 'ALL'>, number> = { '1D': 1, '1W': 7, '1M': 30, '3M': 90, '1Y': 365 };
    const period = this.volumePeriod();
    if (period !== 'ALL' && period !== 'Custom') {
      const cutoff = new Date();
      cutoff.setDate(cutoff.getDate() - periodDays[period as Exclude<Period, 'ALL'>]);
      if (new Date(`${date}T00:00:00`) < cutoff) return false;
    }
    if (period === 'ALL' || period === 'Custom' || period === '3M' || period === '1Y') {
      return date >= this.volumeStartDate() && date <= this.volumeEndDate();
    }
    return true;
  }

  private volumeGroupKey(date: Date): string {
    if (this.volumeGrouping() === 'Hourly') return `${date.toISOString().slice(0, 10)}-${date.getUTCHours()}`;
    if (this.volumeGrouping() === 'Monthly') return date.toISOString().slice(0, 7);
    if (this.volumeGrouping() === 'Weekly') {
      const weekStart = new Date(date);
      weekStart.setUTCDate(date.getUTCDate() - date.getUTCDay());
      return weekStart.toISOString().slice(0, 10);
    }
    return date.toISOString().slice(0, 10);
  }

  private volumeLabel(date: Date): string {
    if (this.volumeGrouping() === 'Hourly') return `${date.getUTCHours()}:00`;
    if (this.volumeGrouping() === 'Monthly') return date.toLocaleDateString('en-US', { month: 'short', year: '2-digit' });
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
  }

  private periodCutoff(): number {
    if (this.period() === 'ALL') return 0;
    const days: Record<Exclude<Period, 'ALL'>, number> = { '1D': 1, '1W': 7, '1M': 30, '3M': 90, '1Y': 365 };
    const period = this.period() as Exclude<Period, 'ALL'>;
    return Date.now() - days[period] * 86400000;
  }

  private compareTrades(left: AnalystTrade, right: AnalystTrade): number {
    if (this.sortOrder() === 'Highest value') return right.tradeValue - left.tradeValue;
    if (this.sortOrder() === 'Lowest value') return left.tradeValue - right.tradeValue;
    const comparison = right.executedAt.localeCompare(left.executedAt);
    return this.sortOrder() === 'Oldest' ? -comparison : comparison;
  }
}