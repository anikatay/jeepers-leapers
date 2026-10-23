import {
  AfterViewInit,
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  ViewChild,
  computed,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AccountService } from '../../../../core/services/account.service';
import { AuthService } from '../../../../core/services/auth.service';
import { MarketDataService } from '../../../../core/services/market-data.service';
import { TradesService } from '../../../../core/services/trades.service';
import { TradeModalService } from '../../../../core/services/trade-modal.service';
import { AccountResponse } from '../../../../core/models/account.model';
import { TradeRequest, TradeResponse } from '../../../../core/models/trade.model';
import { SwipeToConfirmComponent } from '../../../../shared/components/swipe-to-confirm.component';
import { getStockIcon } from '../../../../shared/utils/stock-icon';

export type TradeSide = 'BUY' | 'SELL';
export type EntryMode = 'shares' | 'amount';
export type LoadStatus = 'loading' | 'ready' | 'error';

export interface TradePanelInstrument {
  instrumentId: string;
  ticker: string;
  name: string;
  currency: string;
}

interface PanelNotice {
  text: string;
  tone: 'info' | 'error';
}

// Matches the share precision the existing trade modal sent to POST /api/trades.
const QUANTITY_DECIMALS = 6;
const QUANTITY_FACTOR = 10 ** QUANTITY_DECIMALS;

const floorQuantity = (value: number) => Math.floor(value * QUANTITY_FACTOR + 1e-4) / QUANTITY_FACTOR;
const floorCents = (value: number) => Math.floor(value * 100 + 1e-6) / 100;
// Backend rounds trade and settlement values HALF_UP to 4 decimals.
const round4 = (value: number) => Math.round(value * 10_000) / 10_000;

@Component({
  selector: 'app-trade-panel',
  standalone: true,
  imports: [CommonModule, FormsModule, SwipeToConfirmComponent],
  templateUrl: './trade-panel.component.html',
  styleUrl: './trade-panel.component.css'
})
export class TradePanelComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly accountService = inject(AccountService);
  private readonly marketDataService = inject(MarketDataService);
  private readonly tradesService = inject(TradesService);
  private readonly tradeModalService = inject(TradeModalService);

  readonly instrument = input.required<TradePanelInstrument>();
  readonly price = input(0);
  readonly previousClose = input<number | null>(null);
  readonly initialSide = input<TradeSide>('BUY');
  readonly ownedShares = input<number | null>(null);
  readonly holdingsStatus = input<LoadStatus>('loading');

  readonly closed = output<void>();
  readonly tradeCompleted = output<TradeResponse>();

  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;

  readonly Math = Math;

  readonly side = signal<TradeSide>('BUY');
  readonly entryMode = signal<EntryMode>('shares');
  readonly sharesText = signal('');
  readonly amountText = signal('');

  readonly account = signal<AccountResponse | null>(null);
  readonly accountStatus = signal<LoadStatus>('loading');
  readonly fxRate = signal(1);
  readonly fxStatus = signal<LoadStatus>('ready');

  readonly submitting = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly result = signal<TradeResponse | null>(null);
  private readonly failedAttempts = signal(0);

  private readonly accountId = this.auth.session()?.accountId ?? '';
  private readonly subscriptions = new Subscription();
  private previouslyFocused: HTMLElement | null = null;

  readonly icon = computed(() => getStockIcon(this.instrument().ticker));
  readonly instrumentCurrency = computed(() => (this.instrument().currency || 'USD').toUpperCase());
  readonly accountCurrency = computed(() => (this.account()?.currency || 'USD').toUpperCase());
  readonly needsFx = computed(() => this.instrumentCurrency() !== this.accountCurrency());
  readonly cash = computed(() => this.account()?.balance ?? 0);
  readonly owned = computed(() => this.ownedShares() ?? 0);
  readonly canSell = computed(() => this.holdingsStatus() === 'ready' && this.owned() > 0);
  readonly isBuy = computed(() => this.side() === 'BUY');

  /** Price per share in the account's settlement currency. */
  readonly settlementPrice = computed(() => this.price() * (this.needsFx() ? this.fxRate() : 1));

  readonly todayChange = computed(() => {
    const prev = this.previousClose();
    const price = this.price();
    if (!prev || prev <= 0 || price <= 0) return null;
    const amount = price - prev;
    return { amount, pct: (amount / prev) * 100 };
  });

  readonly maxAmount = computed(() => {
    if (this.isBuy()) return Math.max(0, floorCents(this.cash()));
    return Math.max(0, floorCents(this.owned() * this.settlementPrice()));
  });

  readonly amountValue = computed(() => {
    const value = parseFloat(this.amountText());
    return Number.isFinite(value) ? value : 0;
  });

  readonly sliderValue = computed(() => Math.min(Math.max(this.amountValue(), 0), this.maxAmount()));
  readonly sliderPercent = computed(() =>
    this.maxAmount() > 0 ? (this.sliderValue() / this.maxAmount()) * 100 : 0
  );

  readonly quantity = computed(() => {
    if (this.entryMode() === 'shares') {
      const value = parseFloat(this.sharesText());
      return Number.isFinite(value) ? value : 0;
    }

    const unit = this.settlementPrice();
    const amount = this.amountValue();
    if (unit <= 0 || amount <= 0) return 0;

    if (!this.isBuy()) {
      // Selling the full position value maps to the exact owned quantity.
      if (amount >= this.maxAmount() - 0.005) return this.owned();
      return Math.min(floorQuantity(amount / unit), this.owned());
    }

    let qty = floorQuantity(amount / unit);
    while (qty > 0 && this.estimateSettlement(qty) > amount) {
      qty = Math.round((qty - 1 / QUANTITY_FACTOR) * QUANTITY_FACTOR) / QUANTITY_FACTOR;
    }
    return Math.max(0, qty);
  });

  readonly estimatedTotal = computed(() => round4(this.price() * this.quantity()));
  readonly estimatedSettlement = computed(() => this.estimateSettlement(this.quantity()));
  readonly remainingBuyingPower = computed(() => this.cash() - this.estimatedSettlement());

  readonly notice = computed<PanelNotice | null>(() => {
    const isBuy = this.isBuy();
    if (!this.accountId) return { text: 'No active trading account is selected.', tone: 'error' };
    if (this.accountStatus() === 'loading') return { text: 'Loading your available cash…', tone: 'info' };
    if (this.accountStatus() === 'error') {
      return { text: 'Unable to load your available cash. Close the panel and try again.', tone: 'error' };
    }
    if (this.price() <= 0) return { text: 'A live price is not available for this stock right now.', tone: 'error' };
    if (this.fxStatus() === 'loading') return { text: 'Loading currency conversion rate…', tone: 'info' };
    if (this.fxStatus() === 'error') {
      return { text: 'Unable to load the current currency conversion rate.', tone: 'error' };
    }

    if (!isBuy) {
      if (this.holdingsStatus() === 'loading') return { text: 'Loading your holdings…', tone: 'info' };
      if (this.holdingsStatus() === 'error') {
        return { text: 'Unable to load your holdings, so selling is unavailable.', tone: 'error' };
      }
      if (this.owned() <= 0) {
        return { text: `You don't own any shares of ${this.instrument().ticker}.`, tone: 'error' };
      }
    }

    if (this.entryMode() === 'shares') {
      const raw = this.sharesText().trim();
      if (!raw) return { text: 'Enter the number of shares.', tone: 'info' };
      if (!/^\d*\.?\d*$/.test(raw) || !(parseFloat(raw) > 0)) {
        return { text: 'Enter a share quantity greater than 0.', tone: 'error' };
      }
      const decimals = raw.split('.')[1]?.length ?? 0;
      if (decimals > QUANTITY_DECIMALS) {
        return { text: `Share quantities support up to ${QUANTITY_DECIMALS} decimal places.`, tone: 'error' };
      }
    } else {
      const raw = this.amountText().trim();
      if (!raw) return { text: 'Enter a dollar amount or drag the slider.', tone: 'info' };
      if (!/^\d*\.?\d*$/.test(raw) || !(this.amountValue() > 0)) {
        return { text: 'Enter an amount greater than 0.', tone: 'error' };
      }
      if (this.amountValue() > this.maxAmount() + 0.0001) {
        return {
          text: isBuy ? 'This amount is more than your available cash.' : 'This amount is more than the value of the shares you own.',
          tone: 'error'
        };
      }
      if (this.quantity() <= 0) return { text: 'This amount is too small for a single share fraction.', tone: 'error' };
    }

    if (isBuy && this.estimatedSettlement() > this.cash()) {
      return { text: 'Insufficient buying power for this order.', tone: 'error' };
    }
    if (!isBuy && this.quantity() > this.owned()) {
      return { text: `You can sell at most ${this.formatShares(this.owned())} shares.`, tone: 'error' };
    }
    return null;
  });

  readonly canSubmit = computed(() => this.notice() === null && this.quantity() > 0 && !this.submitting());

  /** Any change to the order inputs (or a failed attempt) puts the swipe back at the start. */
  readonly swipeResetKey = computed(
    () => `${this.side()}|${this.entryMode()}|${this.sharesText()}|${this.amountText()}|${this.failedAttempts()}`
  );

  ngOnInit(): void {
    this.side.set(this.initialSide() === 'SELL' && this.canSell() ? 'SELL' : 'BUY');
    this.loadAccount();
  }

  ngAfterViewInit(): void {
    this.previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setTimeout(() => this.closeButton?.nativeElement.focus());
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.previouslyFocused?.focus?.();
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.requestClose();
  }

  requestClose(): void {
    if (this.submitting()) return;
    this.closed.emit();
  }

  setSide(side: TradeSide): void {
    if (this.submitting() || (side === 'SELL' && !this.canSell())) return;
    this.side.set(side);
    this.submitError.set(null);
  }

  setEntryMode(mode: EntryMode): void {
    if (this.submitting()) return;
    this.entryMode.set(mode);
    this.submitError.set(null);
  }

  onSharesChange(value: string | number | null): void {
    this.sharesText.set(value == null ? '' : String(value));
    this.submitError.set(null);
  }

  onAmountChange(value: string | number | null): void {
    this.amountText.set(value == null ? '' : String(value));
    this.submitError.set(null);
  }

  onSliderInput(event: Event): void {
    const value = parseFloat((event.target as HTMLInputElement).value);
    this.onAmountChange(Number.isFinite(value) ? value.toFixed(2) : '');
  }

  setMaxAmount(): void {
    this.onAmountChange(this.maxAmount().toFixed(2));
  }

  /** Called only by a completed swipe. */
  submit(): void {
    if (!this.canSubmit()) {
      this.failedAttempts.update(n => n + 1);
      return;
    }

    const request: TradeRequest = {
      accountId: this.accountId,
      instrumentId: this.instrument().instrumentId,
      side: this.side(),
      quantity: this.quantity()
    };

    this.submitting.set(true);
    this.submitError.set(null);

    this.tradesService.createTrade(request).subscribe({
      next: response => {
        this.submitting.set(false);
        this.result.set(response);
        this.tradeModalService.notifyTradeCompleted();
        this.tradeCompleted.emit(response);
        this.loadAccount();
      },
      error: err => {
        this.submitting.set(false);
        this.submitError.set(err?.error?.message || err?.message || 'Trade failed');
        this.failedAttempts.update(n => n + 1);
        console.error('Trade error:', err);
      }
    });
  }

  startNewOrder(): void {
    this.result.set(null);
    this.submitError.set(null);
    this.sharesText.set('');
    this.amountText.set('');
    if (this.side() === 'SELL' && !this.canSell()) {
      this.side.set('BUY');
    }
  }

  formatCurrency(value: number | null | undefined, currency: string | null | undefined, maxDigits = 2): string {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: Math.max(2, maxDigits)
    }).format(value ?? 0);
  }

  formatShares(value: number | null | undefined): string {
    return new Intl.NumberFormat('en-US', { maximumFractionDigits: QUANTITY_DECIMALS }).format(value ?? 0);
  }

  private estimateSettlement(quantity: number): number {
    const tradeValue = round4(this.price() * quantity);
    return this.needsFx() ? round4(tradeValue * this.fxRate()) : tradeValue;
  }

  private loadAccount(): void {
    if (!this.accountId) {
      this.accountStatus.set('error');
      return;
    }
    if (!this.account()) this.accountStatus.set('loading');

    this.subscriptions.add(
      this.accountService.getAccount(this.accountId).subscribe({
        next: account => {
          this.account.set(account);
          this.accountStatus.set('ready');
          this.loadFxRate();
        },
        error: err => {
          console.error('Failed to load account:', err);
          this.accountStatus.set('error');
        }
      })
    );
  }

  private loadFxRate(): void {
    if (!this.needsFx()) {
      this.fxRate.set(1);
      this.fxStatus.set('ready');
      return;
    }

    this.fxStatus.set('loading');
    this.subscriptions.add(
      this.marketDataService.getExchangeRate(this.instrumentCurrency(), this.accountCurrency()).subscribe({
        next: response => {
          this.fxRate.set(response.rate);
          this.fxStatus.set(response.rate > 0 ? 'ready' : 'error');
        },
        error: err => {
          console.error('Failed to load settlement FX rate:', err);
          this.fxStatus.set('error');
        }
      })
    );
  }
}
