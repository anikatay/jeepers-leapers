import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MarketQuoteService } from '../../core/services/market-quote.service';
import { TranslatePipe } from '../../core/pipes/translate.pipe';

@Component({
  selector: 'app-connection-status',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    <div class="connection-status" [class]="marketQuoteService.status()">
      <span class="dot"></span>
      <span class="label" [ngSwitch]="marketQuoteService.status()">
        <ng-container *ngSwitchCase="'connected'">{{ 'conn.live' | translate }}</ng-container>
        <ng-container *ngSwitchCase="'reconnecting'">{{ 'conn.reconnecting' | translate }}</ng-container>
        <ng-container *ngSwitchDefault>{{ 'conn.offline' | translate }}</ng-container>
      </span>
    </div>
  `,
  styles: [`
    .connection-status { display: inline-flex; align-items: center; gap: 0.4rem; font-size: 0.75rem; color: var(--muted-foreground); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--muted-foreground); flex-shrink: 0; }
    .connection-status.connected .dot { background: var(--gain); }
    .connection-status.reconnecting .dot { background: #f59e0b; animation: pulse 1s infinite; }
    .connection-status.offline .dot { background: var(--loss); }
    @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.25; } }
  `]
})
export class ConnectionStatusComponent {
  constructor(public marketQuoteService: MarketQuoteService) {}
}
