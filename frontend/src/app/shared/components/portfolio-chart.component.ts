import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
  ViewChild
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { PortfolioHistoryPoint } from '../utils/portfolio-history';

const MINUTE_MS = 60_000;

interface ChartColors {
  primary: string;
  border: string;
  muted: string;
}

interface TooltipState {
  visible: boolean;
  left: number;
  top: number;
  date: string;
  value: string;
}

interface RenderedPoint {
  index: number;
  x: number;
  y: number;
}

@Component({
  selector: 'app-portfolio-chart',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div
      #container
      class="portfolio-chart"
      (mousedown)="onMouseDown($event)"
      (mousemove)="onMouseMove($event)"
      (mouseleave)="onMouseLeave()"
    >
      <canvas #canvas aria-label="Portfolio performance chart"></canvas>
      <div
        *ngIf="tooltip.visible"
        class="chart-tooltip"
        [style.left.px]="tooltip.left"
        [style.top.px]="tooltip.top"
      >
        <strong>{{ tooltip.date }}</strong>
        <span>{{ tooltip.value }}</span>
      </div>
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

    canvas {
      display: block;
      width: 100%;
      height: 100%;
      cursor: crosshair;
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
      padding: 0.5rem 0.625rem;
      border: 1px solid var(--border);
      border-radius: 0.5rem;
      background: var(--card);
      color: var(--foreground);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      pointer-events: none;
      font-size: 0.75rem;
      white-space: nowrap;
    }

    .chart-tooltip strong {
      font-weight: 600;
    }

    .chart-tooltip span {
      color: var(--muted-foreground);
      font-family: 'JetBrains Mono', monospace;
    }
  `]
})
export class PortfolioChartComponent implements AfterViewInit, OnChanges, OnDestroy {
  @Input() points: PortfolioHistoryPoint[] = [];

  @ViewChild('container', { static: true })
  private containerRef!: ElementRef<HTMLDivElement>;

  @ViewChild('canvas', { static: true })
  private canvasRef!: ElementRef<HTMLCanvasElement>;

  tooltip: TooltipState = {
    visible: false,
    left: 0,
    top: 0,
    date: '',
    value: ''
  };

  private chartPoints: PortfolioHistoryPoint[] = [];
  private viewStart = 0;
  private viewEnd = 0;
  private followLatest = true;
  private hoverIndex: number | null = null;
  private isPanning = false;
  private panStartX = 0;
  private panStartIndex = 0;
  private panSpan = 0;
  private canvasWidth = 0;
  private canvasHeight = 0;
  private devicePixelRatio = 1;
  private plotLeft = 52;
  private plotRight = 12;
  private plotTop = 14;
  private plotBottom = 30;
  private colors: ChartColors = {
    primary: '#1a6b3c',
    border: '#c8e6d2',
    muted: '#4a7c5a'
  };
  private resizeObserver?: ResizeObserver;
  private themeObserver?: MutationObserver;

  private readonly boundWheelHandler = (event: WheelEvent) => this.handleWheel(event);
  private readonly boundDocumentMouseMove = (event: MouseEvent) => this.handleDocumentMouseMove(event);
  private readonly boundDocumentMouseUp = () => this.handleDocumentMouseUp();

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['points']) return;

    this.chartPoints = (this.points ?? [])
      .filter(point => Number.isFinite(point.value))
      .map(point => ({ ...point }));
    this.resetView();
    this.render();
  }

  ngAfterViewInit(): void {
    const canvas = this.canvasRef.nativeElement;
    canvas.addEventListener('wheel', this.boundWheelHandler, { passive: false });

    this.resolveThemeColors();
    this.themeObserver = typeof MutationObserver === 'undefined'
      ? undefined
      : new MutationObserver(() => {
        this.resolveThemeColors();
        this.render();
      });
    this.themeObserver?.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    });

    this.resizeObserver = typeof ResizeObserver === 'undefined'
      ? undefined
      : new ResizeObserver(() => this.resizeCanvas());
    this.resizeObserver?.observe(this.containerRef.nativeElement);
    this.resizeCanvas();
  }

  updateLivePoint(date: string, value: number): void {
    if (!date || !Number.isFinite(value)) return;

    const timestamp = new Date(date).getTime();
    if (!Number.isFinite(timestamp)) return;

    const minuteTimestamp = Math.floor(timestamp / MINUTE_MS) * MINUTE_MS;
    const lastIndex = this.chartPoints.length - 1;
    const matchingIndex = this.chartPoints.findIndex(point =>
      this.minuteTimestamp(point.date) === minuteTimestamp
    );

    if (matchingIndex >= 0) {
      this.chartPoints[matchingIndex] = {
        date: this.chartPoints[matchingIndex].date,
        value
      };
    } else if (
      lastIndex < 0 ||
      minuteTimestamp > this.minuteTimestamp(this.chartPoints[lastIndex].date)
    ) {
      const isIntraday = date.includes('T') || this.chartPoints.some(point => point.date.includes('T'));
      const lastTimestamp = lastIndex >= 0
        ? this.minuteTimestamp(this.chartPoints[lastIndex].date)
        : null;

      if (
        isIntraday &&
        lastTimestamp !== null &&
        this.isSameUtcDate(lastTimestamp, minuteTimestamp)
      ) {
        for (let backfillTimestamp = lastTimestamp + MINUTE_MS;
             backfillTimestamp < minuteTimestamp;
             backfillTimestamp += MINUTE_MS) {
          this.chartPoints.push({
            date: new Date(backfillTimestamp).toISOString(),
            value: this.chartPoints[lastIndex].value
          });
        }
      }

      this.chartPoints.push({ date, value });
    } else {
      return;
    }

    if (this.followLatest) {
      const span = Math.max(1, this.viewEnd - this.viewStart);
      this.viewEnd = Math.max(0, this.chartPoints.length - 1);
      this.viewStart = Math.max(0, this.viewEnd - span);
    } else {
      this.viewEnd = Math.min(this.viewEnd, Math.max(0, this.chartPoints.length - 1));
    }

    this.render();
  }

  onMouseDown(event: MouseEvent): void {
    if (event.button !== 0 || this.chartPoints.length < 2) return;

    event.preventDefault();
    this.isPanning = true;
    this.panStartX = event.clientX;
    this.panStartIndex = this.viewStart;
    this.panSpan = this.viewEnd - this.viewStart;
    document.addEventListener('mousemove', this.boundDocumentMouseMove);
    document.addEventListener('mouseup', this.boundDocumentMouseUp);
  }

  onMouseMove(event: MouseEvent): void {
    if (this.isPanning || this.chartPoints.length === 0) return;

    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const plot = this.getPlotBounds();
    const ratio = this.clamp((event.clientX - rect.left - plot.left) / plot.width, 0, 1);
    const index = this.clamp(
      Math.round(this.viewStart + ratio * (this.viewEnd - this.viewStart)),
      this.viewStart,
      this.viewEnd
    );

    this.hoverIndex = index;
    const point = this.chartPoints[index];
    const containerRect = this.containerRef.nativeElement.getBoundingClientRect();
    const cursorX = event.clientX - containerRect.left;
    const cursorY = event.clientY - containerRect.top;
    const tooltipWidth = 150;

    this.tooltip = {
      visible: true,
      left: this.clamp(cursorX + 12, 8, Math.max(8, containerRect.width - tooltipWidth - 8)),
      top: Math.max(8, cursorY - 58),
      date: this.formatDate(point.date),
      value: this.formatCurrency(point.value)
    };
    this.render();
  }

  onMouseLeave(): void {
    if (this.isPanning) return;

    this.hoverIndex = null;
    this.tooltip = { ...this.tooltip, visible: false };
    this.render();
  }

  ngOnDestroy(): void {
    this.canvasRef?.nativeElement.removeEventListener('wheel', this.boundWheelHandler);
    this.resizeObserver?.disconnect();
    this.themeObserver?.disconnect();
    this.removeDocumentMouseListeners();
  }

  private handleWheel(event: WheelEvent): void {
    event.preventDefault();
    if (this.chartPoints.length < 2) return;

    const rect = this.canvasRef.nativeElement.getBoundingClientRect();
    const plot = this.getPlotBounds();
    const ratio = this.clamp((event.clientX - rect.left - plot.left) / plot.width, 0, 1);
    const currentSpan = Math.max(1, this.viewEnd - this.viewStart);
    const maxSpan = this.chartPoints.length - 1;
    const minSpan = Math.min(2, maxSpan);
    const zoomFactor = event.deltaY > 0 ? 1.2 : 0.8;
    const nextSpan = this.clamp(currentSpan * zoomFactor, minSpan, maxSpan);
    const anchor = this.viewStart + ratio * currentSpan;
    let nextStart = Math.round(anchor - ratio * nextSpan);
    let nextEnd = nextStart + Math.round(nextSpan);

    if (nextStart < 0) {
      nextStart = 0;
      nextEnd = Math.round(nextSpan);
    }
    if (nextEnd > maxSpan) {
      nextEnd = maxSpan;
      nextStart = Math.max(0, nextEnd - Math.round(nextSpan));
    }

    this.viewStart = nextStart;
    this.viewEnd = nextEnd;
    this.followLatest = this.viewEnd === maxSpan;
    this.render();
  }

  private handleDocumentMouseMove(event: MouseEvent): void {
    if (!this.isPanning || this.chartPoints.length < 2) return;

    event.preventDefault();
    const plot = this.getPlotBounds();
    const indexDelta = Math.round(
      ((event.clientX - this.panStartX) / Math.max(1, plot.width)) * this.panSpan
    );
    const maxStart = Math.max(0, this.chartPoints.length - 1 - this.panSpan);
    this.viewStart = this.clamp(this.panStartIndex - indexDelta, 0, maxStart);
    this.viewEnd = this.viewStart + this.panSpan;
    this.followLatest = this.viewEnd === this.chartPoints.length - 1;
    this.render();
  }

  private handleDocumentMouseUp(): void {
    this.isPanning = false;
    this.removeDocumentMouseListeners();
  }

  private removeDocumentMouseListeners(): void {
    document.removeEventListener('mousemove', this.boundDocumentMouseMove);
    document.removeEventListener('mouseup', this.boundDocumentMouseUp);
  }

  private resetView(): void {
    this.viewStart = 0;
    this.viewEnd = Math.max(0, this.chartPoints.length - 1);
    this.followLatest = true;
    this.hoverIndex = null;
    this.tooltip = { ...this.tooltip, visible: false };
  }

  private resizeCanvas(): void {
    const canvas = this.canvasRef?.nativeElement;
    const container = this.containerRef?.nativeElement;
    if (!canvas || !container) return;

    const rect = container.getBoundingClientRect();
    this.canvasWidth = Math.max(1, Math.floor(rect.width || container.clientWidth || 800));
    this.canvasHeight = Math.max(220, Math.floor(rect.height || container.clientHeight || 260));
    this.devicePixelRatio = Math.max(1, window.devicePixelRatio || 1);
    canvas.width = Math.floor(this.canvasWidth * this.devicePixelRatio);
    canvas.height = Math.floor(this.canvasHeight * this.devicePixelRatio);
    this.render();
  }

  private resolveThemeColors(): void {
    const styles = getComputedStyle(document.documentElement);
    this.colors = {
      primary: styles.getPropertyValue('--primary').trim() || '#1a6b3c',
      border: styles.getPropertyValue('--border').trim() || '#c8e6d2',
      muted: styles.getPropertyValue('--muted-foreground').trim() || '#4a7c5a'
    };
  }

  private render(): void {
    const canvas = this.canvasRef?.nativeElement;
    if (!canvas) return;

    const context = canvas.getContext('2d');
    if (!context) return;

    const width = this.canvasWidth || canvas.clientWidth || 800;
    const height = this.canvasHeight || canvas.clientHeight || 260;
    context.setTransform(this.devicePixelRatio, 0, 0, this.devicePixelRatio, 0, 0);
    context.clearRect(0, 0, width, height);
    if (this.chartPoints.length === 0) return;

    const plot = this.getPlotBounds();
    const start = Math.max(0, Math.floor(this.viewStart));
    const end = Math.min(this.chartPoints.length - 1, Math.ceil(this.viewEnd));
    const visibleValues = this.chartPoints
      .slice(start, end + 1)
      .map(point => point.value);
    let min = Math.min(...visibleValues);
    let max = Math.max(...visibleValues);
    if (min === max) {
      min -= 1;
      max += 1;
    }

    const points: RenderedPoint[] = [];
    for (let index = start; index <= end; index += 1) {
      points.push({
        index,
        x: this.xForIndex(index, plot),
        y: this.yForValue(this.chartPoints[index].value, min, max, plot)
      });
    }
    this.drawAxes(context, plot, min, max, points);

    if (points.length > 0) {
      const gradient = context.createLinearGradient(0, plot.top, 0, plot.bottom);
      gradient.addColorStop(0, this.colorWithAlpha(this.colors.primary, 0.3));
      gradient.addColorStop(1, this.colorWithAlpha(this.colors.primary, 0.02));

      context.beginPath();
      context.moveTo(points[0].x, points[0].y);
      points.slice(1).forEach(point => context.lineTo(point.x, point.y));
      context.lineTo(points[points.length - 1].x, plot.bottom);
      context.lineTo(points[0].x, plot.bottom);
      context.closePath();
      context.fillStyle = gradient;
      context.fill();

      context.beginPath();
      context.moveTo(points[0].x, points[0].y);
      points.slice(1).forEach(point => context.lineTo(point.x, point.y));
      context.strokeStyle = this.colors.primary;
      context.lineWidth = 2;
      context.lineCap = 'round';
      context.lineJoin = 'round';
      context.stroke();

      const latestPoint = points[points.length - 1];
      if (this.chartPoints[latestPoint.index].date.includes('T')) {
        context.fillStyle = this.colors.primary;
        context.beginPath();
        context.arc(latestPoint.x, latestPoint.y, 3, 0, Math.PI * 2);
        context.fill();
      }
    }

    if (this.hoverIndex !== null && this.hoverIndex >= start && this.hoverIndex <= end) {
      const point = this.chartPoints[this.hoverIndex];
      const x = this.xForIndex(this.hoverIndex, plot);
      const y = this.yForValue(point.value, min, max, plot);
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
  }

  private drawAxes(
    context: CanvasRenderingContext2D,
    plot: { left: number; right: number; top: number; bottom: number; width: number; height: number },
    min: number,
    max: number,
    points: RenderedPoint[]
  ): void {
    context.save();
    context.font = '12px Inter, sans-serif';
    context.fillStyle = this.colors.muted;
    context.strokeStyle = this.colors.border;
    context.lineWidth = 1;
    context.globalAlpha = 0.6;
    context.setLineDash([3, 3]);

    for (let step = 0; step < 3; step += 1) {
      const ratio = step / 2;
      const y = plot.top + ratio * plot.height;
      context.beginPath();
      context.moveTo(plot.left, y);
      context.lineTo(plot.right, y);
      context.stroke();
      context.setLineDash([]);
      context.textAlign = 'right';
      context.textBaseline = 'middle';
      context.fillText(this.formatAxisValue(max - ratio * (max - min)), plot.left - 8, y);
      context.setLineDash([3, 3]);
    }

    context.globalAlpha = 1;
    context.textBaseline = 'top';
    const labelCount = points.length === 1
      ? 1
      : Math.min(8, Math.max(2, Math.floor(plot.width / 90) + 1));
    const candidates = Array.from({ length: labelCount }, (_, labelIndex) => {
      const pointIndex = labelCount === 1
        ? 0
        : Math.round(labelIndex * (points.length - 1) / (labelCount - 1));
      const align = labelIndex === 0
        ? 'left'
        : labelIndex === labelCount - 1 ? 'right' : 'center';
      return {
        point: points[pointIndex],
        align: align as CanvasTextAlign
      };
    });
    const labels = candidates.filter((label, index, all) =>
      all.findIndex(candidate => candidate.point.index === label.point.index) === index
    );
    labels.forEach(label => {
      context.textAlign = label.align;
      context.fillText(
        this.formatAxisDate(this.chartPoints[label.point.index].date),
        label.point.x,
        plot.bottom + 8
      );
    });
    context.restore();
  }

  private getPlotBounds(): {
    left: number;
    right: number;
    top: number;
    bottom: number;
    width: number;
    height: number;
  } {
    const width = this.canvasWidth || 800;
    const height = this.canvasHeight || 260;
    const left = this.plotLeft;
    const right = Math.max(left + 1, width - this.plotRight);
    const top = this.plotTop;
    const bottom = Math.max(top + 1, height - this.plotBottom);
    return {
      left,
      right,
      top,
      bottom,
      width: right - left,
      height: bottom - top
    };
  }

  private xForIndex(index: number, plot: { left: number; width: number }): number {
    const span = Math.max(1, this.viewEnd - this.viewStart);
    if (this.chartPoints.length === 1) return plot.left + plot.width / 2;
    return plot.left + ((index - this.viewStart) / span) * plot.width;
  }

  private yForValue(
    value: number,
    min: number,
    max: number,
    plot: { top: number; height: number }
  ): number {
    return plot.top + (1 - (value - min) / (max - min)) * plot.height;
  }

  private formatAxisDate(date: string): string {
    const parsed = new Date(date.length === 10 ? `${date}T00:00:00Z` : date);
    if (Number.isNaN(parsed.getTime())) return date;
    if (date.includes('T')) {
      return parsed.toLocaleTimeString('en-US', {
        hour: 'numeric',
        minute: '2-digit',
        timeZone: 'UTC'
      });
    }
    return parsed.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      timeZone: 'UTC'
    });
  }

  private formatDate(date: string): string {
    const parsed = new Date(date.length === 10 ? `${date}T00:00:00Z` : date);
    if (Number.isNaN(parsed.getTime())) return date;
    if (date.includes('T')) {
      return parsed.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
        timeZone: 'UTC'
      });
    }
    return parsed.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      timeZone: 'UTC'
    });
  }

  private formatCurrency(value: number): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      maximumFractionDigits: 2
    }).format(value);
  }

  private formatAxisValue(value: number): string {
    const absolute = Math.abs(value);
    if (absolute >= 1_000_000) return `$${(value / 1_000_000).toFixed(1)}m`;
    if (absolute >= 1_000) return `$${(value / 1_000).toFixed(1)}k`;
    return `$${Math.round(value)}`;
  }

  private colorWithAlpha(color: string, alpha: number): string {
    const hex = color.replace('#', '');
    if (![3, 6].includes(hex.length) || !/^[0-9a-f]+$/i.test(hex)) return color;

    const normalized = hex.length === 3
      ? hex.split('').map(part => `${part}${part}`).join('')
      : hex;
    const red = Number.parseInt(normalized.slice(0, 2), 16);
    const green = Number.parseInt(normalized.slice(2, 4), 16);
    const blue = Number.parseInt(normalized.slice(4, 6), 16);
    return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
  }

  private clamp(value: number, min: number, max: number): number {
    return Math.min(Math.max(value, min), max);
  }

  private minuteTimestamp(date: string): number {
    const timestamp = new Date(date).getTime();
    return Math.floor(timestamp / MINUTE_MS) * MINUTE_MS;
  }

  private isSameUtcDate(leftTimestamp: number, rightTimestamp: number): boolean {
    const leftDate = new Date(leftTimestamp).toISOString().slice(0, 10);
    const rightDate = new Date(rightTimestamp).toISOString().slice(0, 10);
    return leftDate === rightDate;
  }
}
