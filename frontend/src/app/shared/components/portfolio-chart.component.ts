import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild,
  effect,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioHistoryPoint } from '../utils/portfolio-history';
import { MARKET_TIMEZONE, MARKET_TIMEZONE_LABEL } from '../utils/market-time';
import { TranslationService } from '../../core/services/translation.service';

const MINUTE_MS = 60_000;
const HOUR_MS = 60 * MINUTE_MS;
const DAY_MS = 24 * HOUR_MS;

/** Longest gap the live tail will fill with flat minute bars, so an overnight gap is not filled. */
const BACKFILL_MAX_MINUTES = 420;

/** Fraction of the value range left as headroom so peaks do not sit on the plot border. */
const Y_PADDING_RATIO = 0.06;

/** Rows in the screen-reader table; the series is sampled down to this many. */
const SR_TABLE_ROWS = 48;

/** Pointer travel, in px, before a press becomes a pan rather than a tap. */
const PAN_THRESHOLD_PX = 3;

let nextChartId = 0;

const TIME_STEPS_MS = [
  MINUTE_MS, 2 * MINUTE_MS, 5 * MINUTE_MS, 10 * MINUTE_MS, 15 * MINUTE_MS, 30 * MINUTE_MS,
  HOUR_MS, 2 * HOUR_MS, 3 * HOUR_MS, 6 * HOUR_MS, 12 * HOUR_MS,
  DAY_MS, 2 * DAY_MS, 7 * DAY_MS, 14 * DAY_MS, 30 * DAY_MS, 90 * DAY_MS, 182 * DAY_MS, 365 * DAY_MS
];

interface ChartColors {
  primary: string;
  border: string;
  muted: string;
}

interface TooltipState {
  visible: boolean;
  left: number;
  top: number;
  flip: boolean;
  date: string;
  value: string;
}

export interface TimedPoint {
  date: string;
  value: number;
  timestamp: number;
}

interface Plot {
  left: number;
  right: number;
  top: number;
  bottom: number;
  width: number;
  height: number;
}

interface SrRow {
  time: string;
  value: string;
}

@Component({
  selector: 'app-portfolio-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div #container class="portfolio-chart" [class.is-panning]="isPanning">
      <canvas
        #canvas
        role="img"
        tabindex="0"
        [attr.aria-label]="ariaLabel"
        [attr.aria-describedby]="summaryId"
        (pointerdown)="onPointerDown($event)"
        (pointermove)="onPointerMove($event)"
        (pointerup)="onPointerUp($event)"
        (pointercancel)="onPointerUp($event)"
        (pointerleave)="onPointerLeave($event)"
        (keydown)="onKeyDown($event)"
        (blur)="clearCrosshair()"
      ></canvas>

      <div
        *ngIf="tooltip.visible"
        class="chart-tooltip"
        [class.flip]="tooltip.flip"
        [style.left.px]="tooltip.left"
        [style.top.px]="tooltip.top"
      >
        <strong>{{ tooltip.date }}</strong>
        <span>{{ tooltip.value }}</span>
      </div>

      <button
        *ngIf="!followLatest && hasPoints"
        type="button"
        class="chart-jump"
        (click)="jumpToLatest()"
      >{{ label('chart.jump_latest') }}</button>

      <p [id]="summaryId" class="sr-only">{{ summary }}</p>

      <table class="sr-only">
        <caption>{{ label(tableCaptionKey) }}</caption>
        <thead>
          <tr>
            <th scope="col">{{ label('chart.col_time') }}</th>
            <th scope="col">{{ label('chart.col_value') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let row of srRows">
            <th scope="row">{{ row.time }}</th>
            <td>{{ row.value }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 220px;
    }

    .portfolio-chart {
      position: relative;
      width: 100%;
      height: 260px;
      min-height: 220px;
      user-select: none;
    }

    .portfolio-chart > table.sr-only {
      display: block;
    }

    canvas {
      display: block;
      width: 100%;
      height: 100%;
      cursor: crosshair;
      /* Horizontal drags pan the chart; vertical ones stay with the page scroller. */
      touch-action: pan-y;
    }

    canvas:focus-visible {
      outline: 2px solid var(--ring, var(--primary));
      outline-offset: 2px;
    }

    .portfolio-chart.is-panning canvas {
      cursor: grabbing;
    }

    .chart-tooltip {
      position: absolute;
      z-index: 1;
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
      min-width: 8.5rem;
      max-width: min(16rem, 70%);
      padding: 0.5rem 0.625rem;
      border: 1px solid var(--border);
      border-radius: 0.5rem;
      background: var(--card);
      color: var(--foreground);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      pointer-events: none;
      font-size: 0.75rem;
    }

    /* Anchored past the cursor near the right edge, so long locale strings stay inside. */
    .chart-tooltip.flip {
      transform: translateX(-100%);
    }

    .chart-tooltip strong {
      font-weight: 600;
    }

    .chart-tooltip span {
      color: var(--muted-foreground);
      font-family: 'JetBrains Mono', monospace;
    }

    .chart-jump {
      position: absolute;
      inset-block-end: 0.5rem;
      inset-inline-end: 0.5rem;
      z-index: 2;
      padding: 0.25rem 0.625rem;
      border: 1px solid var(--border);
      border-radius: 999px;
      background: var(--card);
      color: var(--foreground);
      font-size: 0.6875rem;
      font-weight: 600;
      cursor: pointer;
    }

    .chart-jump:hover {
      background: var(--secondary);
    }
  `]
})
export class PortfolioChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() points: PortfolioHistoryPoint[] = [];

  /** Settlement currency for the axis and tooltip; accounts are not all USD. */
  @Input() currency = 'USD';

  /**
   * Translation keys for the accessible description, so the same chart can present a portfolio
   * total or a single instrument's price without claiming to be the other.
   */
  @Input() ariaLabelKey = 'chart.aria_label';
  @Input() summaryKey = 'chart.summary';
  @Input() emptySummaryKey = 'chart.summary_empty';
  @Input() tableCaptionKey = 'chart.table_caption';

  /** Unique so two charts on one page cannot collide on aria-describedby. */
  readonly summaryId = `portfolio-chart-summary-${nextChartId++}`;

  @ViewChild('container', { static: true })
  private containerRef!: ElementRef<HTMLDivElement>;

  @ViewChild('canvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  private readonly translation = inject(TranslationService);

  tooltip: TooltipState = {
    visible: false,
    left: 0,
    top: 0,
    flip: false,
    date: '',
    value: ''
  };

  isPanning = false;
  followLatest = true;
  srRows: SrRow[] = [];

  private chartPoints: TimedPoint[] = [];
  private viewStartMs = 0;
  private viewEndMs = 0;
  private hoverTimestamp: number | null = null;
  private canvasWidth = 0;
  private canvasHeight = 0;
  private pixelRatio = 1;
  private fontSize = 12;
  private readonly plotRight = 12;
  private readonly plotTop = 14;
  private colors: ChartColors = {
    primary: '#1a6b3c',
    border: '#c8e6d2',
    muted: '#4a7c5a'
  };

  private readonly activePointers = new Map<number, { x: number; y: number }>();
  private panAnchor: { x: number; startMs: number; endMs: number } | null = null;
  private pinchAnchor: { distance: number; startMs: number; endMs: number; ratio: number } | null = null;

  private renderHandle: number | null = null;
  private renderPending = false;
  private resizeObserver?: ResizeObserver;
  private themeObserver?: MutationObserver;

  private readonly boundWheelHandler = (event: WheelEvent) => this.handleWheel(event);
  private readonly boundVisibilityHandler = () => {
    if (!document.hidden && this.renderPending) this.scheduleRender();
  };
  private readonly boundWindowResize = () => this.resizeCanvas();

  constructor() {
    // Re-label the axis and tooltip when the active language changes.
    effect(() => {
      this.translation.currentLanguage();
      this.rebuildScreenReaderRows();
      this.scheduleRender();
    });
  }

  get hasPoints(): boolean {
    return this.chartPoints.length > 0;
  }

  get ariaLabel(): string {
    return this.label(this.ariaLabelKey);
  }

  get summary(): string {
    if (this.chartPoints.length === 0) return this.label(this.emptySummaryKey);

    const values = this.chartPoints.map(point => point.value);
    return this.translation.t(this.summaryKey, {
      count: this.chartPoints.length,
      start: this.formatDate(this.chartPoints[0]),
      end: this.formatDate(this.chartPoints[this.chartPoints.length - 1]),
      low: this.formatCurrency(Math.min(...values)),
      high: this.formatCurrency(Math.max(...values)),
      first: this.formatCurrency(values[0]),
      last: this.formatCurrency(values[values.length - 1])
    });
  }

  label(key: string): string {
    return this.translation.t(key);
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['currency'] && !changes['points']) {
      this.rebuildScreenReaderRows();
      this.scheduleRender();
      return;
    }
    if (!changes['points']) return;

    const previousFirst = this.chartPoints[0]?.timestamp ?? null;
    const previousSpan = this.viewEndMs - this.viewStartMs;
    const wasFollowing = this.followLatest;

    this.chartPoints = (this.points ?? [])
      .map(point => ({
        date: point.date,
        value: point.value,
        timestamp: parseTimestamp(point.date)
      }))
      .filter(point => Number.isFinite(point.value) && Number.isFinite(point.timestamp))
      .sort((a, b) => a.timestamp - b.timestamp);

    // A refresh that extends the same series keeps the viewer's zoom and pan; a genuinely
    // different series (period change, different holdings) resets to the full range.
    const isExtension =
      previousFirst !== null &&
      previousSpan > 0 &&
      this.chartPoints.length > 0 &&
      this.chartPoints[0].timestamp === previousFirst;

    if (isExtension) {
      this.followLatest = wasFollowing;
      this.applyViewWindow(this.viewStartMs, this.viewEndMs, previousSpan);
    } else {
      this.resetView();
    }

    this.rebuildScreenReaderRows();
    this.scheduleRender();
  }

  ngAfterViewInit(): void {
    const canvas = this.canvasRef.nativeElement;
    canvas.addEventListener('wheel', this.boundWheelHandler, { passive: false });
    document.addEventListener('visibilitychange', this.boundVisibilityHandler);
    window.addEventListener('resize', this.boundWindowResize);

    this.resolveThemeColors();
    this.themeObserver = typeof MutationObserver === 'undefined'
      ? undefined
      : new MutationObserver(() => {
        this.resolveThemeColors();
        this.scheduleRender();
      });
    // The appearance service swaps theme classes and sets data-text-size on <html>.
    this.themeObserver?.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class', 'data-text-size']
    });

    this.resizeObserver = typeof ResizeObserver === 'undefined'
      ? undefined
      : new ResizeObserver(() => this.resizeCanvas());
    this.resizeObserver?.observe(this.containerRef.nativeElement);
    this.resizeCanvas();
  }

  ngOnDestroy(): void {
    this.canvasRef?.nativeElement.removeEventListener('wheel', this.boundWheelHandler);
    document.removeEventListener('visibilitychange', this.boundVisibilityHandler);
    window.removeEventListener('resize', this.boundWindowResize);
    this.resizeObserver?.disconnect();
    this.themeObserver?.disconnect();
    if (this.renderHandle !== null) cancelAnimationFrame(this.renderHandle);
  }

  updateLivePoint(date: string, value: number): void {
    if (!date || !Number.isFinite(value)) return;

    const timestamp = parseTimestamp(date);
    if (!Number.isFinite(timestamp)) return;

    const minuteTimestamp = Math.floor(timestamp / MINUTE_MS) * MINUTE_MS;
    const lastIndex = this.chartPoints.length - 1;
    const matchingIndex = this.chartPoints.findIndex(
      point => Math.floor(point.timestamp / MINUTE_MS) * MINUTE_MS === minuteTimestamp
    );

    if (matchingIndex >= 0) {
      if (this.chartPoints[matchingIndex].value === value) return;
      this.chartPoints[matchingIndex] = { ...this.chartPoints[matchingIndex], value };
    } else if (lastIndex < 0 || minuteTimestamp > this.chartPoints[lastIndex].timestamp) {
      const lastPoint = lastIndex >= 0 ? this.chartPoints[lastIndex] : null;
      const gapMinutes = lastPoint
        ? Math.floor((minuteTimestamp - lastPoint.timestamp) / MINUTE_MS)
        : 0;

      // Flat-fill a within-session gap so the line does not slope across idle minutes.
      if (lastPoint && gapMinutes > 1 && gapMinutes <= BACKFILL_MAX_MINUTES) {
        for (let fill = lastPoint.timestamp + MINUTE_MS; fill < minuteTimestamp; fill += MINUTE_MS) {
          this.chartPoints.push({
            date: new Date(fill).toISOString(),
            value: lastPoint.value,
            timestamp: fill
          });
        }
      }

      this.chartPoints.push({ date, value, timestamp: minuteTimestamp });
    } else {
      return;
    }

    const span = this.viewEndMs - this.viewStartMs;
    this.applyViewWindow(this.viewStartMs, this.viewEndMs, span > 0 ? span : undefined);
    this.rebuildScreenReaderRows();
    this.scheduleRender();
  }

  jumpToLatest(): void {
    const span = this.viewEndMs - this.viewStartMs;
    this.followLatest = true;
    this.applyViewWindow(this.viewStartMs, this.viewEndMs, span > 0 ? span : undefined);
    this.scheduleRender();
  }

  clearCrosshair(): void {
    if (this.hoverTimestamp === null && !this.tooltip.visible) return;
    this.hoverTimestamp = null;
    this.tooltip = { ...this.tooltip, visible: false };
    this.scheduleRender();
  }

  onPointerDown(event: PointerEvent): void {
    if (event.pointerType === 'mouse' && event.button !== 0) return;
    if (this.chartPoints.length === 0) return;

    this.canvasRef.nativeElement.setPointerCapture?.(event.pointerId);
    this.activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY });

    if (this.activePointers.size === 2) {
      this.beginPinch();
      this.panAnchor = null;
      return;
    }

    this.panAnchor = { x: event.clientX, startMs: this.viewStartMs, endMs: this.viewEndMs };
  }

  onPointerMove(event: PointerEvent): void {
    if (this.activePointers.has(event.pointerId)) {
      this.activePointers.set(event.pointerId, { x: event.clientX, y: event.clientY });
    }

    if (this.activePointers.size >= 2 && this.pinchAnchor) {
      this.applyPinch();
      return;
    }

    if (this.panAnchor) {
      const travel = Math.abs(event.clientX - this.panAnchor.x);
      if (!this.isPanning && travel < PAN_THRESHOLD_PX) return;

      this.isPanning = true;
      this.clearCrosshair();
      const plot = this.getPlotBounds();
      const span = this.panAnchor.endMs - this.panAnchor.startMs;
      const deltaMs = ((this.panAnchor.x - event.clientX) / Math.max(1, plot.width)) * span;
      // Release the follow lock first, or the window would be pinned back to the live edge.
      this.followLatest = false;
      this.applyViewWindow(this.panAnchor.startMs + deltaMs, this.panAnchor.endMs + deltaMs, span);
      this.scheduleRender();
      return;
    }

    if (event.pointerType === 'mouse') this.moveCrosshairTo(event.clientX, event.clientY);
  }

  onPointerUp(event: PointerEvent): void {
    const wasPanning = this.isPanning;
    this.activePointers.delete(event.pointerId);
    this.canvasRef.nativeElement.releasePointerCapture?.(event.pointerId);

    if (this.activePointers.size < 2) this.pinchAnchor = null;
    if (this.activePointers.size === 0) {
      this.panAnchor = null;
      this.isPanning = false;
    }

    // A tap that never became a pan places the crosshair, which is the only way
    // to read a value on a touch screen.
    if (!wasPanning && event.pointerType !== 'mouse') {
      this.moveCrosshairTo(event.clientX, event.clientY);
    }
  }

  onPointerLeave(event: PointerEvent): void {
    if (event.pointerType === 'mouse' && !this.isPanning) this.clearCrosshair();
  }

  onKeyDown(event: KeyboardEvent): void {
    if (this.chartPoints.length === 0) return;

    const span = this.viewEndMs - this.viewStartMs;
    const lastTimestamp = this.chartPoints[this.chartPoints.length - 1].timestamp;

    switch (event.key) {
      case 'ArrowRight':
      case 'ArrowLeft': {
        const direction = event.key === 'ArrowRight' ? 1 : -1;
        if (event.shiftKey) {
          const step = span * 0.25 * direction;
          this.applyViewWindow(this.viewStartMs + step, this.viewEndMs + step, span);
        } else {
          this.stepCrosshair(direction);
        }
        break;
      }
      case '+':
      case '=':
        this.zoomBy(0.8, 0.5);
        break;
      case '-':
      case '_':
        this.zoomBy(1.25, 0.5);
        break;
      case 'Home':
        this.hoverTimestamp = this.chartPoints[0].timestamp;
        this.syncTooltipToCrosshair();
        break;
      case 'End':
        this.hoverTimestamp = lastTimestamp;
        this.syncTooltipToCrosshair();
        break;
      case 'Escape':
        this.clearCrosshair();
        break;
      default:
        return;
    }

    event.preventDefault();
    this.scheduleRender();
  }

  private handleWheel(event: WheelEvent): void {
    if (this.chartPoints.length < 2) return;

    const bounds = this.dataBounds();
    const totalSpan = Math.max(MINUTE_MS, bounds.end - bounds.start);
    const atFullExtent = this.viewEndMs - this.viewStartMs >= totalSpan - 1;
    if (event.deltaY > 0 && atFullExtent) return;

    event.preventDefault();
    const plot = this.getPlotBounds();
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const ratio = clamp((event.clientX - rect.left - plot.left) / Math.max(1, plot.width), 0, 1);
    this.zoomBy(event.deltaY > 0 ? 1.2 : 0.8, ratio);
    this.scheduleRender();
  }
  

  private beginPinch(): void {
    const [a, b] = [...this.activePointers.values()];
    const plot = this.getPlotBounds();
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const centerX = (a.x + b.x) / 2;

    this.pinchAnchor = {
      distance: Math.max(1, Math.abs(a.x - b.x)),
      startMs: this.viewStartMs,
      endMs: this.viewEndMs,
      ratio: clamp((centerX - rect.left - plot.left) / Math.max(1, plot.width), 0, 1)
    };
  }

  private applyPinch(): void {
    if (!this.pinchAnchor) return;

    const [a, b] = [...this.activePointers.values()];
    const distance = Math.max(1, Math.abs(a.x - b.x));
    const anchorSpan = this.pinchAnchor.endMs - this.pinchAnchor.startMs;
    const nextSpan = anchorSpan * (this.pinchAnchor.distance / distance);
    const anchorMs = this.pinchAnchor.startMs + this.pinchAnchor.ratio * anchorSpan;

    this.setSpanAround(anchorMs, this.pinchAnchor.ratio, nextSpan);
    this.scheduleRender();
  }

  private zoomBy(factor: number, ratio: number): void {
    const span = this.viewEndMs - this.viewStartMs;
    const anchorMs = this.viewStartMs + ratio * span;
    this.setSpanAround(anchorMs, ratio, span * factor);
  }

  private setSpanAround(anchorMs: number, ratio: number, requestedSpan: number): void {
    const bounds = this.dataBounds();
    const totalSpan = Math.max(MINUTE_MS, bounds.end - bounds.start);
    const minSpan = Math.max(MINUTE_MS, totalSpan / 400);
    const span = clamp(requestedSpan, minSpan, totalSpan);

    // Zoom around the anchor rather than the live edge; applyViewWindow restores the follow
    // lock on its own if the resulting window still ends at the latest point.
    this.followLatest = false;
    this.applyViewWindow(anchorMs - ratio * span, anchorMs + (1 - ratio) * span, span);
  }

  /** Clamps a requested window into the data range, honouring follow-the-latest. */
  private applyViewWindow(startMs: number, endMs: number, preferredSpan?: number): void {
    const bounds = this.dataBounds();
    const totalSpan = Math.max(MINUTE_MS, bounds.end - bounds.start);
    const span = clamp(preferredSpan ?? endMs - startMs, Math.max(MINUTE_MS, totalSpan / 400), totalSpan);

    let nextStart = this.followLatest ? bounds.end - span : startMs;
    if (nextStart < bounds.start) nextStart = bounds.start;
    if (nextStart + span > bounds.end) nextStart = bounds.end - span;

    this.viewStartMs = nextStart;
    this.viewEndMs = nextStart + span;
    this.followLatest = this.viewEndMs >= bounds.end - 1;
  }

  private dataBounds(): { start: number; end: number } {
    if (this.chartPoints.length === 0) return { start: 0, end: MINUTE_MS };

    const start = this.chartPoints[0].timestamp;
    const end = this.chartPoints[this.chartPoints.length - 1].timestamp;
    // A single point (or one repeated minute) still needs a window with width.
    if (end - start < MINUTE_MS) return { start: start - 30 * MINUTE_MS, end: end + 30 * MINUTE_MS };
    return { start, end };
  }

  private resetView(): void {
    const bounds = this.dataBounds();
    this.viewStartMs = bounds.start;
    this.viewEndMs = bounds.end;
    this.followLatest = true;
    this.hoverTimestamp = null;
    this.tooltip = { ...this.tooltip, visible: false };
  }

  private moveCrosshairTo(clientX: number, clientY: number): void {
    if (this.chartPoints.length === 0) return;

    const plot = this.getPlotBounds();
    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const x = clamp(clientX - rect.left, plot.left, plot.right);
    const nearest = this.nearestPoint(this.timestampForX(x, plot));
    if (!nearest) return;

    this.hoverTimestamp = nearest.timestamp;
    this.positionTooltip(nearest, clientY - rect.top);
    this.scheduleRender();
  }

  private stepCrosshair(direction: number): void {
    const current = this.hoverTimestamp === null
      ? this.nearestPoint(this.viewStartMs + (this.viewEndMs - this.viewStartMs) / 2)
      : this.nearestPoint(this.hoverTimestamp);
    if (!current) return;

    const index = this.chartPoints.indexOf(current);
    const next = this.chartPoints[clamp(index + direction, 0, this.chartPoints.length - 1)];
    this.hoverTimestamp = next.timestamp;

    // Keep the crosshair inside the window by panning when it reaches an edge.
    const span = this.viewEndMs - this.viewStartMs;
    if (next.timestamp < this.viewStartMs || next.timestamp > this.viewEndMs) {
      this.followLatest = false;
      this.applyViewWindow(next.timestamp - span / 2, next.timestamp + span / 2, span);
    }
    this.syncTooltipToCrosshair();
  }

  private syncTooltipToCrosshair(): void {
    if (this.hoverTimestamp === null) return;

    const point = this.nearestPoint(this.hoverTimestamp);
    if (!point) return;

    const plot = this.getPlotBounds();
    const x = this.xForTimestamp(point.timestamp, plot);
    this.positionTooltip(point, plot.top + plot.height / 2, x);
  }

  private positionTooltip(point: TimedPoint, cursorY: number, cursorX?: number): void {
    const container = this.containerRef.nativeElement.getBoundingClientRect();
    const plot = this.getPlotBounds();
    const x = cursorX ?? this.xForTimestamp(point.timestamp, plot);
    const flip = x > container.width / 2;

    this.tooltip = {
      visible: true,
      flip,
      left: clamp(flip ? x - 12 : x + 12, 8, Math.max(8, container.width - 8)),
      top: clamp(cursorY - 58, 8, Math.max(8, container.height - 72)),
      date: this.formatDate(point),
      value: this.formatCurrency(point.value)
    };
  }

  private nearestPoint(timestamp: number): TimedPoint | null {
    if (this.chartPoints.length === 0) return null;

    const index = lowerBound(this.chartPoints, timestamp);
    const before = this.chartPoints[index - 1];
    const after = this.chartPoints[index];
    if (!before) return after ?? null;
    if (!after) return before;

    return timestamp - before.timestamp <= after.timestamp - timestamp ? before : after;
  }

  /** Coalesces every interaction and live tick into at most one paint per frame. */
  private scheduleRender(): void {
    if (document.hidden) {
      this.renderPending = true;
      return;
    }
    if (this.renderHandle !== null) return;

    this.renderHandle = requestAnimationFrame(() => {
      this.renderHandle = null;
      this.renderPending = false;
      this.render();
    });
  }

  private resizeCanvas(): void {
    const canvas = this.canvasRef?.nativeElement;
    const container = this.containerRef?.nativeElement;
    if (!canvas || !container) return;

    const rect = container.getBoundingClientRect();
    this.canvasWidth = Math.max(1, Math.floor(rect.width || container.clientWidth || 800));
    this.canvasHeight = Math.max(220, Math.floor(rect.height || container.clientHeight || 260));
    this.pixelRatio = Math.max(1, window.devicePixelRatio || 1);
    canvas.width = Math.floor(this.canvasWidth * this.pixelRatio);
    canvas.height = Math.floor(this.canvasHeight * this.pixelRatio);
    this.scheduleRender();
  }

  private resolveThemeColors(): void {
    const styles = getComputedStyle(document.documentElement);
    this.colors = {
      primary: styles.getPropertyValue('--primary').trim() || '#1a6b3c',
      border: styles.getPropertyValue('--border').trim() || '#c8e6d2',
      muted: styles.getPropertyValue('--muted-foreground').trim() || '#4a7c5a'
    };
    // Scale canvas text with the app's text-size preference, which only moves the root size.
    const rootSize = parseFloat(styles.fontSize) || 16;
    this.fontSize = clamp(Math.round(rootSize * 0.75), 10, 20);
  }

  private render(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const context = canvas.getContext('2d');
    if (!context) return;

    const width = this.canvasWidth || canvas.clientWidth || 800;
    const height = this.canvasHeight || canvas.clientHeight || 260;
    context.setTransform(this.pixelRatio, 0, 0, this.pixelRatio, 0, 0);
    context.clearRect(0, 0, width, height);
    if (this.chartPoints.length === 0) return;

    const plot = this.getPlotBounds();
    const visible = this.visibleSlice();
    const scale = this.valueScale(visible);
    this.drawAxes(context, plot, scale);

    if (visible.length > 0) {
      // The window is a time range, so points can fall outside the plot; clip to it.
      context.save();
      context.beginPath();
      context.rect(plot.left, plot.top, plot.width, plot.height);
      context.clip();

      const coords = visible.map(point => ({
        x: this.xForTimestamp(point.timestamp, plot),
        y: this.yForValue(point.value, scale, plot)
      }));

      this.fillArea(context, coords, plot);

      context.beginPath();
      context.moveTo(coords[0].x, coords[0].y);
      coords.slice(1).forEach(coord => context.lineTo(coord.x, coord.y));
      context.strokeStyle = this.colors.primary;
      context.lineWidth = 2;
      context.lineCap = 'round';
      context.lineJoin = 'round';
      context.stroke();

      const latest = this.chartPoints[this.chartPoints.length - 1];
      if (latest.date.includes('T') && latest.timestamp <= this.viewEndMs) {
        context.fillStyle = this.colors.primary;
        context.beginPath();
        context.arc(
          this.xForTimestamp(latest.timestamp, plot),
          this.yForValue(latest.value, scale, plot),
          3,
          0,
          Math.PI * 2
        );
        context.fill();
      }

      context.restore();
    }

    this.drawCrosshair(context, plot, scale);
  }

  private fillArea(
    context: CanvasRenderingContext2D,
    coords: Array<{ x: number; y: number }>,
    plot: Plot
  ): void {
    const translucent = withAlpha(this.colors.primary, 0.3);
    const faded = withAlpha(this.colors.primary, 0.02);

    context.beginPath();
    context.moveTo(coords[0].x, coords[0].y);
    coords.slice(1).forEach(coord => context.lineTo(coord.x, coord.y));
    context.lineTo(coords[coords.length - 1].x, plot.bottom);
    context.lineTo(coords[0].x, plot.bottom);
    context.closePath();

    if (translucent && faded) {
      const gradient = context.createLinearGradient(0, plot.top, 0, plot.bottom);
      gradient.addColorStop(0, translucent);
      gradient.addColorStop(1, faded);
      context.fillStyle = gradient;
      context.fill();
      return;
    }

    // Unparseable theme colour: flat wash rather than no fill at all.
    context.save();
    context.globalAlpha = 0.15;
    context.fillStyle = this.colors.primary;
    context.fill();
    context.restore();
  }

  private drawCrosshair(context: CanvasRenderingContext2D, plot: Plot, scale: ValueScale): void {
    if (this.hoverTimestamp === null) return;

    const point = this.nearestPoint(this.hoverTimestamp);
    if (!point || point.timestamp < this.viewStartMs || point.timestamp > this.viewEndMs) return;

    const x = this.xForTimestamp(point.timestamp, plot);
    const y = this.yForValue(point.value, scale, plot);

    context.save();
    context.strokeStyle = this.colors.muted;
    context.globalAlpha = 0.7;
    context.setLineDash([4, 4]);
    context.beginPath();
    context.moveTo(x, plot.top);
    context.lineTo(x, plot.bottom);
    context.stroke();
    context.setLineDash([]);
    context.fillStyle = this.colors.primary;
    context.beginPath();
    context.arc(x, y, 4, 0, Math.PI * 2);
    context.fill();
    context.restore();
  }

  private drawAxes(context: CanvasRenderingContext2D, plot: Plot, scale: ValueScale): void {
    context.save();
    context.font = `${this.fontSize}px Inter, sans-serif`;
    context.fillStyle = this.colors.muted;
    context.strokeStyle = this.colors.border;
    context.lineWidth = 1;

    for (let step = 0; step < 3; step += 1) {
      const ratio = step / 2;
      const y = plot.top + ratio * plot.height;
      context.globalAlpha = 0.6;
      context.setLineDash([3, 3]);
      context.beginPath();
      context.moveTo(plot.left, y);
      context.lineTo(plot.right, y);
      context.stroke();
      context.setLineDash([]);
      context.globalAlpha = 1;
      context.textAlign = 'right';
      context.textBaseline = 'middle';
      context.fillText(
        this.formatAxisValue(scale.max - ratio * (scale.max - scale.min)),
        plot.left - 8,
        y
      );
    }

    context.textBaseline = 'top';
    this.timeTicks(plot).forEach(tick => {
      const x = this.xForTimestamp(tick, plot);
      if (x < plot.left - 1 || x > plot.right + 1) return;

      // Keep the first and last labels inside the plot instead of overhanging it.
      const nearStart = x - plot.left < this.fontSize * 2;
      const nearEnd = plot.right - x < this.fontSize * 2;
      context.textAlign = nearStart ? 'left' : nearEnd ? 'right' : 'center';
      context.fillText(this.formatAxisTime(tick), clamp(x, plot.left, plot.right), plot.bottom + 8);
    });

    context.restore();
  }

  /** Evenly spaced "nice" instants across the window, so gaps read as gaps. */
  private timeTicks(plot: Plot): number[] {
    const span = this.viewEndMs - this.viewStartMs;
    const slots = clamp(Math.floor(plot.width / (this.fontSize * 6)), 2, 8);
    const step = TIME_STEPS_MS.find(candidate => candidate >= span / slots)
      ?? TIME_STEPS_MS[TIME_STEPS_MS.length - 1];

    const ticks: number[] = [];
    for (let tick = Math.ceil(this.viewStartMs / step) * step; tick <= this.viewEndMs; tick += step) {
      ticks.push(tick);
    }
    return ticks;
  }

  /** Visible points plus one on each side, so the line enters and leaves the plot edges. */
  private visibleSlice(): TimedPoint[] {
    const from = Math.max(0, lowerBound(this.chartPoints, this.viewStartMs) - 1);
    const to = Math.min(this.chartPoints.length, lowerBound(this.chartPoints, this.viewEndMs) + 1);
    return this.chartPoints.slice(from, to);
  }

  private valueScale(visible: TimedPoint[]): ValueScale {
    const source = visible.length > 0 ? visible : this.chartPoints;
    if (source.length === 0) return { min: 0, max: 1 };

    let min = Infinity;
    let max = -Infinity;
    source.forEach(point => {
      if (point.value < min) min = point.value;
      if (point.value > max) max = point.value;
    });

    if (min === max) {
      // Relative, so a flat line reads the same at $50 and at $5M.
      const epsilon = Math.max(Math.abs(min) * 0.001, 0.01);
      return { min: min - epsilon, max: max + epsilon };
    }

    const padding = (max - min) * Y_PADDING_RATIO;
    return { min: min - padding, max: max + padding };
  }

  private getPlotBounds(): Plot {
    const width = this.canvasWidth || 800;
    const height = this.canvasHeight || 260;
    // Reserve room for the widest axis label at the current text size.
    const left = Math.max(44, this.fontSize * 4.5);
    const right = Math.max(left + 1, width - this.plotRight);
    const top = this.plotTop;
    const bottom = Math.max(top + 1, height - (this.fontSize + 18));

    return { left, right, top, bottom, width: right - left, height: bottom - top };
  }

  private xForTimestamp(timestamp: number, plot: Plot): number {
    const span = Math.max(1, this.viewEndMs - this.viewStartMs);
    return plot.left + ((timestamp - this.viewStartMs) / span) * plot.width;
  }

  private timestampForX(x: number, plot: Plot): number {
    const span = Math.max(1, this.viewEndMs - this.viewStartMs);
    return this.viewStartMs + ((x - plot.left) / Math.max(1, plot.width)) * span;
  }

  private yForValue(value: number, scale: ValueScale, plot: Plot): number {
    const span = scale.max - scale.min || 1;
    return plot.top + (1 - (value - scale.min) / span) * plot.height;
  }

  private rebuildScreenReaderRows(): void {
    const total = this.chartPoints.length;
    if (total === 0) {
      this.srRows = [];
      return;
    }

    const stride = Math.max(1, Math.ceil(total / SR_TABLE_ROWS));
    const rows: SrRow[] = [];
    for (let index = 0; index < total; index += stride) {
      rows.push({
        time: this.formatDate(this.chartPoints[index]),
        value: this.formatCurrency(this.chartPoints[index].value)
      });
    }

    const last = this.chartPoints[total - 1];
    if ((total - 1) % stride !== 0) {
      rows.push({ time: this.formatDate(last), value: this.formatCurrency(last.value) });
    }
    this.srRows = rows;
  }

  private get locale(): string {
    return this.translation.currentLanguage();
  }

  private formatAxisTime(timestamp: number): string {
    const intraday = this.viewEndMs - this.viewStartMs < 3 * DAY_MS;
    if (intraday) {
      return new Date(timestamp).toLocaleTimeString(this.locale, {
        hour: 'numeric',
        minute: '2-digit',
        timeZone: MARKET_TIMEZONE
      });
    }
    return new Date(timestamp).toLocaleDateString(this.locale, {
      month: 'short',
      day: 'numeric',
      timeZone: MARKET_TIMEZONE
    });
  }

  private formatDate(point: TimedPoint): string {
    const parsed = new Date(point.timestamp);
    if (Number.isNaN(parsed.getTime())) return point.date;

    if (point.date.includes('T')) {
      const stamp = parsed.toLocaleString(this.locale, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        timeZone: MARKET_TIMEZONE
      });
      return `${stamp} ${MARKET_TIMEZONE_LABEL}`;
    }
    return parsed.toLocaleDateString(this.locale, {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      timeZone: MARKET_TIMEZONE
    });
  }

  private formatCurrency(value: number): string {
    return new Intl.NumberFormat(this.locale, {
      style: 'currency',
      currency: this.currency || 'USD',
      maximumFractionDigits: 2
    }).format(value);
  }

  private formatAxisValue(value: number): string {
    return new Intl.NumberFormat(this.locale, {
      style: 'currency',
      currency: this.currency || 'USD',
      notation: 'compact',
      maximumFractionDigits: 1
    }).format(value);
  }
}

interface ValueScale {
  min: number;
  max: number;
}

function parseTimestamp(date: string): number {
  return new Date(date.length === 10 ? `${date}T00:00:00Z` : date).getTime();
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

/** First index whose timestamp is >= the target. Exported for tests. */
export function lowerBound(points: TimedPoint[], timestamp: number): number {
  let low = 0;
  let high = points.length;
  while (low < high) {
    const mid = (low + high) >>> 1;
    if (points[mid].timestamp < timestamp) low = mid + 1;
    else high = mid;
  }
  return low;
}

/** Returns an rgba() string for hex or rgb() input, or null when the format is unknown. Exported for tests. */
export function withAlpha(color: string, alpha: number): string | null {
  const hex = color.replace('#', '');
  if ([3, 6].includes(hex.length) && /^[0-9a-f]+$/i.test(hex)) {
    const full = hex.length === 3 ? hex.split('').map(part => `${part}${part}`).join('') : hex;
    const red = Number.parseInt(full.slice(0, 2), 16);
    const green = Number.parseInt(full.slice(2, 4), 16);
    const blue = Number.parseInt(full.slice(4, 6), 16);
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
  }

  const channels = color.match(/-?\d*\.?\d+/g);
  if (/^rgba?\(/i.test(color) && channels && channels.length >= 3) {
    return `rgba(${channels[0]}, ${channels[1]}, ${channels[2]}, ${alpha})`;
  }
  return null;
}
