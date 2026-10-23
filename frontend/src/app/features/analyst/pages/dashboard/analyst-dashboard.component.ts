import { CommonModule } from '@angular/common';
import { Component, HostListener, WritableSignal, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  AnalystDataSource,
  AnalystApiDataSource,
  AnalystSnapshot,
  AnalystTrade,
  PriceBar
} from '../../data-access/analyst-data-source';

type Period = '1D' | '1W' | '1M' | '3M' | '1Y' | 'ALL';
type ChartMode = 'line' | 'candles';
type Flow = 'BUY' | 'SELL' | 'BOTH' | 'NET';
type VolumeView = 'bars' | 'line' | 'area';
type Side = 'ALL' | 'BUY' | 'SELL';
type Sort = 'newest' | 'oldest' | 'value' | 'pnl';
type Menu = 'compare' | 'volume' | 'filters';

interface Scale {
  min: number;
  max: number;
  ticks: number[];
}

interface Kpi {
  label: string;
  value: string;
  tone?: 'up' | 'down';
  delta?: number;
  note: string;
}

interface Bucket {
  date: string;
  buy: number;
  sell: number;
  net: number;
  trades: number;
}

const PERIOD_SESSIONS: Record<Period, number> = { '1D': 1, '1W': 5, '1M': 21, '3M': 63, '1Y': 252, ALL: Infinity };
const MAX_SERIES = 4;
const MAX_CANDLES = 80;
const FLAT_BAND = 0.005;
const TABLE_LIMIT = 100;
const DONUT_RADIUS = 38.3;

const SORTS: Record<Sort, (left: AnalystTrade, right: AnalystTrade) => number> = {
  newest: (left, right) => right.executedAt.localeCompare(left.executedAt),
  oldest: (left, right) => left.executedAt.localeCompare(right.executedAt),
  value: (left, right) => right.tradeValue - left.tradeValue,
  pnl: (left, right) => right.pnl - left.pnl
};

const usd = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
const usdCents = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });
const usdCompact = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 2 });
const count = new Intl.NumberFormat('en-US');
const compact = new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 });

@Component({
  selector: 'app-analyst-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  providers: [{ provide: AnalystDataSource, useClass: AnalystApiDataSource }],
  templateUrl: './analyst-dashboard.component.html',
  styleUrl: './analyst-dashboard.component.css'
})
export class AnalystDashboardComponent {
  readonly periods: Period[] = ['1D', '1W', '1M', '3M', '1Y', 'ALL'];
  readonly flows: { key: Flow; label: string }[] = [
    { key: 'BOTH', label: 'Buy & Sell' },
    { key: 'BUY', label: 'Buy' },
    { key: 'SELL', label: 'Sell' },
    { key: 'NET', label: 'Net Flow' }
  ];
  readonly maxSeries = MAX_SERIES;
  readonly tableLimit = TABLE_LIMIT;

  readonly snapshot = signal<AnalystSnapshot | null>(null);
  readonly error = signal(false);
  readonly menu = signal<Menu | null>(null);
  readonly period = signal<Period>('3M');
  readonly mode = signal<ChartMode>('line');
  readonly selected = signal<string[]>([]);
  readonly compareQuery = signal('');
  readonly hover = signal<number | null>(null);
  readonly outcomeHover = signal<string | null>(null);
  readonly volumePeriod = signal<Period>('3M');
  readonly flow = signal<Flow>('BOTH');
  readonly volumeView = signal<VolumeView>('bars');
  readonly volumeHover = signal<number | null>(null);
  readonly search = signal('');
  readonly side = signal<Side>('ALL');
  readonly symbol = signal('ALL');
  readonly account = signal('ALL');
  readonly dateFrom = signal('');
  readonly dateTo = signal('');
  readonly sort = signal<Sort>('newest');

  readonly instruments = computed(() => this.snapshot()?.instruments ?? []);

  readonly compareOptions = computed(() => {
    const query = this.compareQuery().trim().toLowerCase();
    return this.instruments().filter(item => !query || `${item.ticker} ${item.name}`.toLowerCase().includes(query));
  });

  private readonly sessions = computed(() => {
    const snapshot = this.snapshot();
    return snapshot ? (snapshot.history[snapshot.instruments[0]?.ticker] ?? []).map(bar => bar.date) : [];
  });

  readonly periodTrades = computed(() => this.tradesIn(this.period()));

  readonly kpis = computed<Kpi[]>(() => {
    const period = this.period();
    const { from, previous } = this.window(period);
    const now = summarize(this.periodTrades());
    const prior = previous === null ? null : summarize((this.snapshot()?.trades ?? []).filter(trade => {
      const day = trade.executedAt.slice(0, 10);
      return day > previous && day <= from;
    }));
    const span = period === '1D' ? 'session' : period;
    const note = prior ? `vs prior ${span}` : 'All recorded activity';
    return [
      { label: 'Total Trading Volume', value: usdCompact.format(now.volume), delta: change(now.volume, prior?.volume), note },
      { label: 'Total Trades', value: count.format(now.trades), delta: change(now.trades, prior?.trades), note },
      { label: 'Active Accounts', value: count.format(now.accounts), delta: change(now.accounts, prior?.accounts), note },
      {
        label: 'Net P/L',
        value: signed(now.pnl, usd),
        tone: now.pnl >= 0 ? 'up' : 'down',
        note: prior ? `Prior ${span}: ${signed(prior.pnl, usd)}` : 'Realized and marked to last close'
      }
    ];
  });

  readonly outcomes = computed(() => {
    const accounts = new Map<string, { pnl: number; notional: number }>();
    for (const trade of this.periodTrades()) {
      const account = accounts.get(trade.accountId) ?? { pnl: 0, notional: 0 };
      account.pnl += trade.pnl;
      account.notional += trade.tradeValue;
      accounts.set(trade.accountId, account);
    }
    const parts = {
      up: { key: 'up', label: 'Profitable', count: 0, pnl: 0 },
      down: { key: 'down', label: 'Losing', count: 0, pnl: 0 },
      flat: { key: 'flat', label: 'Flat', count: 0, pnl: 0 }
    };
    for (const { pnl, notional } of accounts.values()) {
      const part = Math.abs(pnl) <= notional * FLAT_BAND ? parts.flat : pnl > 0 ? parts.up : parts.down;
      part.count++;
      part.pnl += pnl;
    }
    const total = accounts.size;
    const list = Object.values(parts);
    const gap = list.filter(part => part.count).length > 1 ? 1.2 : 0;
    let offset = 0;
    const segments = list.map(part => {
      const pct = total ? (part.count / total) * 100 : 0;
      const length = Math.max(0, pct - gap);
      const angle = ((offset + pct / 2) / 100) * 2 * Math.PI;
      const segment = {
        ...part,
        pct,
        dash: `${length} ${100 - length}`,
        offset: -offset,
        tipX: 50 + DONUT_RADIUS * Math.sin(angle),
        tipY: 50 - DONUT_RADIUS * Math.cos(angle)
      };
      offset += pct;
      return segment;
    });
    const majority = total ? segments.reduce((best, part) => (part.count > best.count ? part : best)) : null;
    return { total, segments, majority };
  });

  readonly hoveredOutcome = computed(() => this.outcomes().segments.find(part => part.key === this.outcomeHover()) ?? null);

  readonly series = computed(() => this.selected().map((ticker, index) => {
    const bars = this.barsFor(ticker);
    const base = bars[0]?.close || 1;
    const values = bars.map(bar => (bar.close / base - 1) * 100);
    const name = this.instruments().find(item => item.ticker === ticker)?.name ?? ticker;
    return { ticker, name, bars, values, color: `var(--s${index + 1})`, change: values[values.length - 1] ?? 0 };
  }));

  readonly candleSpan = computed(() => Math.max(1, Math.ceil(this.barsFor(this.selected()[0]).length / MAX_CANDLES)));

  readonly candles = computed(() => groupBars(this.barsFor(this.selected()[0]), this.candleSpan()));

  readonly primary = computed(() => {
    const bars = this.candles();
    const instrument = this.instruments().find(item => item.ticker === this.selected()[0]);
    return bars.length && instrument
      ? { ...instrument, close: bars[bars.length - 1].close, change: (bars[bars.length - 1].close / bars[0].open - 1) * 100 }
      : null;
  });

  readonly dates = computed(() => (this.mode() === 'line' ? this.series()[0]?.bars ?? [] : this.candles()).map(bar => bar.date));

  readonly scale = computed(() => niceScale(this.mode() === 'line'
    ? this.series().flatMap(item => item.values)
    : this.candles().flatMap(bar => [bar.high, bar.low])));

  readonly volumeMax = computed(() => Math.max(1, ...this.candles().map(bar => bar.volume)));

  readonly buckets = computed(() => {
    const period = this.volumePeriod();
    const buckets = new Map<string, Bucket>();
    for (const trade of this.tradesIn(period)) {
      const key = bucketKey(trade.executedAt, period);
      const bucket = buckets.get(key) ?? { date: key, buy: 0, sell: 0, net: 0, trades: 0 };
      if (trade.side === 'BUY') bucket.buy += trade.tradeValue;
      else bucket.sell += trade.tradeValue;
      bucket.net = bucket.buy - bucket.sell;
      bucket.trades++;
      buckets.set(key, bucket);
    }
    return [...buckets.values()].sort((left, right) => left.date.localeCompare(right.date));
  });

  readonly volumeSeries = computed(() => ({
    dates: this.buckets().map(bucket => bucket.date),
    buy: this.buckets().map(bucket => bucket.buy),
    sell: this.buckets().map(bucket => bucket.sell),
    flow: this.buckets().map(bucket => this.flowValue(bucket))
  }));

  readonly volumeTotals = computed(() => {
    const buy = this.buckets().reduce((sum, bucket) => sum + bucket.buy, 0);
    const sell = this.buckets().reduce((sum, bucket) => sum + bucket.sell, 0);
    return { buy, sell, net: buy - sell };
  });

  readonly volumeScale = computed(() => {
    const flow = this.flow();
    const stacked = flow === 'BOTH' && this.volumeView() === 'bars';
    return niceScale([0, ...this.buckets().flatMap(bucket =>
      flow === 'BOTH' ? (stacked ? [bucket.buy + bucket.sell] : [bucket.buy, bucket.sell]) : [this.flowValue(bucket)])], 4);
  });

  readonly volumeViews = computed(() => [
    { key: 'bars' as const, label: this.flow() === 'BOTH' ? 'Stacked bar' : 'Bar' },
    { key: 'line' as const, label: 'Line' },
    { key: 'area' as const, label: 'Area' }
  ]);

  readonly volumeLabel = computed(() =>
    `${this.flows.find(item => item.key === this.flow())!.label} · ${this.volumeViews().find(item => item.key === this.volumeView())!.label}`);

  readonly accounts = computed(() => [...new Set(this.periodTrades().map(trade => trade.accountId))].sort());

  readonly activeFilters = computed(() =>
    [this.symbol() !== 'ALL', this.side() !== 'ALL', this.account() !== 'ALL', !!this.dateFrom(), !!this.dateTo()].filter(Boolean).length);

  readonly activity = computed(() => {
    const query = this.search().trim().toLowerCase();
    const from = this.dateFrom();
    const to = this.dateTo();
    return this.periodTrades()
      .filter(trade => {
        const day = trade.executedAt.slice(0, 10);
        return (this.side() === 'ALL' || trade.side === this.side()) &&
          (this.symbol() === 'ALL' || trade.ticker === this.symbol()) &&
          (this.account() === 'ALL' || trade.accountId === this.account()) &&
          (!from || day >= from) && (!to || day <= to) &&
          (!query || `${trade.ticker} ${trade.name} ${trade.accountId}`.toLowerCase().includes(query));
      })
      .sort(SORTS[this.sort()]);
  });

  constructor(private dataSource: AnalystDataSource) {
    this.load();
  }

  @HostListener('document:click')
  @HostListener('document:keydown.escape')
  closeMenu(): void {
    this.menu.set(null);
  }

  load(): void {
    this.error.set(false);
    this.dataSource.load().subscribe({
      next: snapshot => {
        this.snapshot.set(snapshot);
        this.selected.set(snapshot.instruments.slice(0, 2).map(item => item.ticker));
      },
      error: () => this.error.set(true)
    });
  }

  toggleMenu(menu: Menu, event: Event): void {
    event.stopPropagation();
    this.compareQuery.set('');
    this.menu.set(this.menu() === menu ? null : menu);
  }

  toggleSeries(ticker: string): void {
    const selected = this.selected();
    if (this.mode() === 'candles') {
      this.selected.set([ticker, ...selected.filter(item => item !== ticker)].slice(0, MAX_SERIES));
      this.menu.set(null);
    } else if (selected.includes(ticker)) {
      if (selected.length > 1) this.selected.set(selected.filter(item => item !== ticker));
    } else if (selected.length < MAX_SERIES) {
      this.selected.set([...selected, ticker]);
    }
  }

  clearFilters(): void {
    this.symbol.set('ALL');
    this.side.set('ALL');
    this.account.set('ALL');
    this.dateFrom.set('');
    this.dateTo.set('');
  }

  flowValue(bucket: Bucket): number {
    const flow = this.flow();
    return flow === 'BUY' ? bucket.buy : flow === 'SELL' ? bucket.sell : flow === 'NET' ? bucket.net : bucket.buy + bucket.sell;
  }

  x(index: number, total: number): number {
    return ((index + 0.5) / Math.max(total, 1)) * 100;
  }

  y(value: number, scale: Scale): number {
    return 100 - ((value - scale.min) / (scale.max - scale.min)) * 100;
  }

  path(values: number[], scale: Scale, fill = false): string {
    const points = values.map((value, index): [number, number] => [this.x(index, values.length), this.y(value, scale)]);
    const line = monotonePath(points);
    if (!fill || !points.length) return line;
    const base = this.y(Math.max(scale.min, 0), scale);
    return `${line} L${points[points.length - 1][0]},${base} L${points[0][0]},${base} Z`;
  }

  barWidth(total: number, ratio = 0.62): number {
    return Math.min(6, (100 / Math.max(total, 1)) * ratio);
  }

  box(from: number, to: number, scale: Scale): { y: number; height: number } {
    const top = this.y(Math.max(from, to), scale);
    return { y: top, height: Math.max(0.35, this.y(Math.min(from, to), scale) - top) };
  }

  track(event: MouseEvent, total: number, target: WritableSignal<number | null>): void {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    if (!total || !rect.width) return;
    target.set(Math.min(total - 1, Math.max(0, Math.floor(((event.clientX - rect.left) / rect.width) * total))));
  }

  xTicks(dates: string[], period: Period): { x: number; label: string }[] {
    if (dates.length < 2) return [];
    const steps = Math.min(5, dates.length - 1);
    return Array.from({ length: steps + 1 }, (_, step) => {
      const index = Math.round((step / steps) * (dates.length - 1));
      return { x: this.x(index, dates.length), label: this.when(dates[index], period, false) };
    });
  }

  when(date: string, period: Period, detailed = true): string {
    if (date.includes('T')) {
      return detailed ? `${formatDate(date.slice(0, 10), { month: 'short', day: 'numeric' })}, ${this.time(date)}` : this.time(date, false);
    }
    if (period === 'ALL') return formatDate(date, detailed ? { month: 'long', year: 'numeric' } : { month: 'short', year: '2-digit' });
    if (!detailed) return formatDate(date, period === '1Y' ? { month: 'short', year: '2-digit' } : { month: 'short', day: 'numeric' });
    return formatDate(date, { month: 'short', day: 'numeric', year: 'numeric' });
  }

  time(iso: string, zone = true): string {
    const time = new Date(iso).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', timeZone: 'America/New_York' });
    return zone ? `${time} ET` : time;
  }

  bucketLabel(date: string): string {
    const period = this.volumePeriod();
    return period === '1Y' ? `Week of ${this.when(date, period)}` : this.when(date, period);
  }

  span(period: Period): string {
    return period === 'ALL' ? 'all time' : period === '1D' ? 'today' : period;
  }

  axis(value: number, kind: 'percent' | 'price' | 'money'): string {
    if (kind === 'percent') return `${value > 0 ? '+' : ''}${Number(value.toFixed(1))}%`;
    return kind === 'price' ? usd.format(value) : usdCompact.format(value);
  }

  percent(value: number): string {
    return `${value >= 0 ? '+' : '−'}${Math.abs(value).toFixed(2)}%`;
  }

  money(value: number): string {
    return usdCents.format(value);
  }

  whole(value: number): string {
    return usd.format(value);
  }

  pnl(value: number): string {
    return signed(value, usd);
  }

  volume(value: number): string {
    return compact.format(value);
  }

  downloadCsv(): void {
    const header = ['Date', 'Time (UTC)', 'Account', 'Symbol', 'Side', 'Quantity', 'Price', 'Notional', 'P/L'];
    const rows = this.activity().map(trade => [
      trade.executedAt.slice(0, 10), trade.executedAt.slice(11, 16), trade.accountId, trade.ticker,
      trade.side, trade.quantity, trade.executionPrice, trade.tradeValue, trade.pnl
    ]);
    const url = URL.createObjectURL(new Blob([[header, ...rows].map(row => row.join(',')).join('\n')], { type: 'text/csv' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `trading-activity-${this.period().toLowerCase()}-${this.snapshot()?.asOf ?? 'export'}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  private window(period: Period): { from: string; previous: string | null } {
    const sessions = this.sessions();
    const back = PERIOD_SESSIONS[period];
    const last = sessions.length - 1;
    if (back >= last) return { from: '', previous: null };
    return { from: sessions[last - back], previous: sessions[last - 2 * back] ?? null };
  }

  private tradesIn(period: Period): AnalystTrade[] {
    const { from } = this.window(period);
    return (this.snapshot()?.trades ?? []).filter(trade => trade.executedAt.slice(0, 10) > from);
  }

  private barsFor(ticker: string | undefined): PriceBar[] {
    const snapshot = this.snapshot();
    if (!snapshot || !ticker) return [];
    if (this.period() === '1D') return snapshot.intraday[ticker] ?? [];
    const { from } = this.window(this.period());
    return (snapshot.history[ticker] ?? []).filter(bar => bar.date > from);
  }
}

function summarize(trades: AnalystTrade[]) {
  return {
    volume: trades.reduce((sum, trade) => sum + trade.tradeValue, 0),
    trades: trades.length,
    accounts: new Set(trades.map(trade => trade.accountId)).size,
    pnl: trades.reduce((sum, trade) => sum + trade.pnl, 0)
  };
}

function change(current: number, previous: number | undefined): number | undefined {
  return previous ? ((current - previous) / previous) * 100 : undefined;
}

function signed(value: number, format: Intl.NumberFormat): string {
  const text = format.format(Math.abs(value));
  return value > 0 ? `+${text}` : value < 0 ? `−${text}` : text;
}

function bucketKey(executedAt: string, period: Period): string {
  if (period === '1D') return `${executedAt.slice(0, 13)}:00:00Z`;
  if (period === 'ALL') return `${executedAt.slice(0, 7)}-01`;
  if (period !== '1Y') return executedAt.slice(0, 10);
  const day = new Date(`${executedAt.slice(0, 10)}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() - ((day.getUTCDay() + 6) % 7));
  return day.toISOString().slice(0, 10);
}

function groupBars(bars: PriceBar[], size: number): PriceBar[] {
  if (size <= 1) return bars;
  const groups: PriceBar[] = [];
  for (let start = 0; start < bars.length; start += size) {
    const chunk = bars.slice(start, start + size);
    groups.push({
      date: chunk[0].date,
      open: chunk[0].open,
      close: chunk[chunk.length - 1].close,
      high: Math.max(...chunk.map(bar => bar.high)),
      low: Math.min(...chunk.map(bar => bar.low)),
      volume: chunk.reduce((sum, bar) => sum + bar.volume, 0)
    });
  }
  return groups;
}

// Monotone cubic (Fritsch–Carlson): smooth joins without overshooting real highs and lows.
function monotonePath(points: [number, number][]): string {
  const n = points.length;
  if (n < 3) return points.map((point, i) => `${i ? 'L' : 'M'}${point}`).join(' ');
  const slopes = points.slice(1).map((point, i) => (point[1] - points[i][1]) / (point[0] - points[i][0]));
  const tangents = points.map((_, i) => {
    if (i === 0) return slopes[0];
    if (i === n - 1) return slopes[n - 2];
    return slopes[i - 1] * slopes[i] <= 0 ? 0 : (2 * slopes[i - 1] * slopes[i]) / (slopes[i - 1] + slopes[i]);
  });
  let path = `M${points[0]}`;
  for (let i = 1; i < n; i++) {
    const [x0, y0] = points[i - 1];
    const [x1, y1] = points[i];
    const dx = (x1 - x0) / 3;
    path += ` C${x0 + dx},${y0 + tangents[i - 1] * dx} ${x1 - dx},${y1 - tangents[i] * dx} ${x1},${y1}`;
  }
  return path;
}

function niceScale(values: number[], steps = 5): Scale {
  if (!values.length) return { min: 0, max: 1, ticks: [0, 1] };
  const low = Math.min(...values);
  const high = Math.max(...values);
  const raw = (high - low || Math.abs(high) || 1) / steps;
  const magnitude = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 2.5, 5, 10].map(factor => factor * magnitude).find(candidate => candidate >= raw)!;
  const min = Math.floor(low / step) * step;
  const max = Math.ceil(high / step) * step;
  const ticks: number[] = [];
  for (let value = min; value <= max + step / 2; value += step) ticks.push(Number(value.toFixed(6)));
  return { min, max, ticks };
}

function formatDate(date: string, options: Intl.DateTimeFormatOptions): string {
  return new Date(`${date}T00:00:00Z`).toLocaleDateString('en-US', { ...options, timeZone: 'UTC' });
}
