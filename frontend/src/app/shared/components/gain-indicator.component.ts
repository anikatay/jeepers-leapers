import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-gain-indicator',
  standalone: true,
  imports: [CommonModule],
  template: `
    <span class="gain-indicator" [class.positive]="gain >= 0" [class.negative]="gain < 0">
      <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
        <path *ngIf="gain >= 0" d="M12 19V5M5 12l7-7 7 7" stroke-linecap="round" stroke-linejoin="round"/>
        <path *ngIf="gain < 0" d="M12 5v14M5 12l7 7 7-7" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
      <span class="amount">{{ sign }}{{ prefix }}{{ absGain | number:'1.2-2' }}</span>
      <span class="pct" *ngIf="showPct">({{ sign }}{{ absGainPct | number:'1.2-2' }}%)</span>
    </span>
  `,
  styles: [`
    .gain-indicator { display: inline-flex; align-items: center; gap: 0.25rem; font-weight: 600; font-size: 0.875rem; white-space: nowrap; transition: color 2000ms ease; }
    .gain-indicator.positive { color: var(--gain); }
    .gain-indicator.negative { color: var(--loss); }
    .arrow { width: 1em; height: 1em; flex-shrink: 0; }
    .pct { opacity: 0.8; font-weight: 500; }
  `]
})
export class GainIndicatorComponent {
  @Input() gain = 0;
  @Input() gainPct = 0;
  @Input() showPct = true;
  @Input() prefix = '$';

  get sign(): string { return this.gain >= 0 ? '+' : '-'; }
  get absGain(): number { return Math.abs(this.gain); }
  get absGainPct(): number { return Math.abs(this.gainPct); }
}
