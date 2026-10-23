import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-sparkline',
  standalone: true,
  imports: [CommonModule],
  template: `
    <svg *ngIf="points.length > 1"
         [attr.viewBox]="'0 0 ' + width + ' ' + height"
         [attr.width]="width" [attr.height]="height"
         preserveAspectRatio="none" class="sparkline">
      <path [attr.d]="linePath" fill="none" [style.stroke]="color" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  `,
  styles: [`.sparkline { display: block; }`]
})
export class SparklineComponent {
  @Input() set values(v: number[] | null | undefined) { this.points = v ?? []; }
  @Input() width = 80;
  @Input() height = 28;

  points: number[] = [];

  get color(): string {
    if (this.points.length < 2) return 'var(--muted-foreground)';
    return this.points[this.points.length - 1] >= this.points[0] ? 'var(--gain)' : 'var(--loss)';
  }

  get linePath(): string {
    const n = this.points.length;
    if (n < 2) return '';

    const min = Math.min(...this.points);
    const max = Math.max(...this.points);
    const range = max - min || 1;
    const stepX = this.width / (n - 1);

    return this.points
      .map((v, i) => {
        const x = i * stepX;
        const y = this.height - ((v - min) / range) * this.height;
        return `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`;
      })
      .join(' ');
  }
}
