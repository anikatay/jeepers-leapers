import { Component, ElementRef, HostListener, OnDestroy, OnInit, ViewChild, signal } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, takeUntil, tap } from 'rxjs';
import { AdminDashboardRow } from '../../../../core/models/admin-dashboard.model';
import { TradeResponse } from '../../../../core/models/trade.model';
import { AdminDashboardService } from '../../../../core/services/admin-dashboard.service';

@Component({
  selector: 'app-admin-home',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-home.component.html',
  styleUrl: './admin-home.component.css'
})
export class AdminHomeComponent implements OnInit, OnDestroy {
  readonly rows = signal<AdminDashboardRow[]>([]);
  readonly selectedUser = signal<AdminDashboardRow | null>(null);
  readonly selectedUserTrades = signal<TradeResponse[]>([]);
  readonly loading = signal(false);
  readonly loadingTrades = signal(false);
  readonly error = signal<string | null>(null);
  readonly tradesError = signal<string | null>(null);
  readonly freezeError = signal<string | null>(null);
  readonly freezeSubmitting = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly pageSize = 10;
  readonly tradePage = signal(0);
  readonly tradeTotalPages = signal(0);
  readonly tradeTotalItems = signal(0);
  readonly tradePageSize = 7;
  readonly holdingsTableCollapsed = signal(false);
  readonly tradingPanelCollapsed = signal(false);
  readonly detailPanelFullscreen = signal(false);
  readonly detailPanelHeight = signal(560);
  readonly detailPanelTopOffset = signal(0);

  searchTerm = '';
  tradeSearchTerm = '';
  freezeReason = '';
  columns: { header: string }[] = [
    { header: 'Name' },
    { header: 'Account Value' },
    { header: 'Balance' },
    { header: 'Portfolio Value' },
    { header: 'Profit' },
    { header: 'Trades' },
    { header: 'Created At' },
    { header: 'Status' }
  ];

  private readonly searchChanges = new Subject<string>();
  private readonly tradeSearchChanges = new Subject<string>();
  private readonly destroy = new Subject<void>();
  private resizingDetailPanel = false;
  private resizeStartY = 0;
  private resizeStartHeight = 0;
  private resizeStartTopOffset = 0;

  @ViewChild('userDetailLayout')
  private userDetailLayout?: ElementRef<HTMLElement>;

  constructor(
    private readonly adminDashboardService: AdminDashboardService
  ) {}

  ngOnInit(): void {
    this.searchChanges.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      tap(() => this.page.set(0)),
      switchMap(search => this.loadDashboardRequest(search, 0)),
      takeUntil(this.destroy)
    ).subscribe();

    this.tradeSearchChanges.pipe(
      debounceTime(250),
      distinctUntilChanged(),
      tap(() => this.tradePage.set(0)),
      takeUntil(this.destroy)
    ).subscribe(() => {
      const accountId = this.selectedUser()?.accountId;
      if (accountId) {
        this.loadSelectedUserTrades(accountId, 0);
      }
    });

    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.destroy.next();
    this.destroy.complete();
    this.searchChanges.complete();
    this.tradeSearchChanges.complete();
  }

  onSearchChanged(): void {
    this.searchChanges.next(this.searchTerm);
  }

  onTradeSearchChanged(): void {
    this.tradeSearchChanges.next(this.tradeSearchTerm);
  }

  loadDashboard(page = this.page()): void {
    this.loadDashboardRequest(this.searchTerm, page)
      .pipe(takeUntil(this.destroy))
      .subscribe();
  }

  private loadDashboardRequest(search: string, page: number) {
    this.loading.set(true);
    this.error.set(null);

    return this.adminDashboardService.getDashboard(search, page, this.pageSize).pipe(
      tap(response => {
        this.rows.set(response.items);
        this.page.set(response.page);
        this.totalPages.set(response.totalPages);
        this.totalItems.set(response.totalItems);

        const selected = this.selectedUser();
        if (selected && !response.items.some(row => row.accountId === selected.accountId)) {
          this.clearSelection();
        }
        this.loading.set(false);
      }),
      catchError(() => {
        this.rows.set([]);
        this.loading.set(false);
        this.error.set('Unable to load the admin dashboard.');
        return of(null);
      })
    );
  }

  selectUser(user: AdminDashboardRow): void {
    const previousSelection = this.selectedUser();
    const sameAccount = previousSelection?.accountId === user.accountId;
    this.selectedUser.set(user);
    this.tradesError.set(null);
    this.freezeError.set(null);
    this.freezeReason = '';

    if (!sameAccount) {
      this.tradeSearchTerm = '';
    }
    const requestedPage = sameAccount ? this.tradePage() : 0;
    this.tradePage.set(requestedPage);
    this.loadSelectedUserTrades(user.accountId, requestedPage);
  }

  private loadSelectedUserTrades(accountId: string, page: number): void {
    this.selectedUserTrades.set([]);
    this.loadingTrades.set(true);
    this.tradesError.set(null);
    this.adminDashboardService.getAccountTrades(
      accountId,
      page,
      this.tradePageSize,
      this.tradeSearchTerm
    ).pipe(
      takeUntil(this.destroy),
      catchError(() => {
        this.tradesError.set('Unable to load trades for this account.');
        return of(null);
      })
    ).subscribe(response => {
      if (!response) {
        this.loadingTrades.set(false);
        return;
      }
      if (this.selectedUser()?.accountId === accountId) {
        this.selectedUserTrades.set(response.items);
        this.tradePage.set(response.page);
        this.tradeTotalPages.set(response.totalPages);
        this.tradeTotalItems.set(response.totalItems);
        this.loadingTrades.set(false);
      }
    });
  }

  previousTradesPage(): void {
    if (this.tradePage() > 0) {
      const accountId = this.selectedUser()?.accountId;
      if (accountId) {
        this.loadSelectedUserTrades(accountId, this.tradePage() - 1);
      }
    }
  }

  nextTradesPage(): void {
    if (this.tradePage() + 1 < this.tradeTotalPages()) {
      const accountId = this.selectedUser()?.accountId;
      if (accountId) {
        this.loadSelectedUserTrades(accountId, this.tradePage() + 1);
      }
    }
  }

  clearSelection(): void {
    this.exitDetailPanelFullscreen();
    this.selectedUser.set(null);
    this.selectedUserTrades.set([]);
    this.tradePage.set(0);
    this.tradeTotalPages.set(0);
    this.tradeTotalItems.set(0);
    this.tradeSearchTerm = '';
    this.tradesError.set(null);
    this.freezeError.set(null);
    this.freezeReason = '';
  }

  isFrozen(user: AdminDashboardRow): boolean {
    return user.accountStatus === 'FROZEN';
  }

  freezeSelectedAccount(): void {
    const user = this.selectedUser();
    const reason = this.freezeReason.trim();
    if (!user || this.isFrozen(user) || !reason || this.freezeSubmitting()) {
      if (!reason) {
        this.freezeError.set('A reason is required to freeze an account.');
      }
      return;
    }

    this.freezeSubmitting.set(true);
    this.freezeError.set(null);
    this.adminDashboardService.freezeAccount(user.accountId, reason).pipe(
      takeUntil(this.destroy)
    ).subscribe({
      next: account => {
        this.updateAccountState(account.accountId, account.status, account.freezeReason);
        this.freezeReason = '';
        this.freezeSubmitting.set(false);
      },
      error: error => {
        this.freezeError.set(error?.error?.details || error?.error?.message || 'Unable to freeze this account.');
        this.freezeSubmitting.set(false);
      }
    });
  }

  unfreezeSelectedAccount(): void {
    const user = this.selectedUser();
    if (!user || !this.isFrozen(user) || this.freezeSubmitting()) {
      return;
    }

    this.freezeSubmitting.set(true);
    this.freezeError.set(null);
    this.adminDashboardService.unfreezeAccount(user.accountId).pipe(
      takeUntil(this.destroy)
    ).subscribe({
      next: account => {
        this.updateAccountState(account.accountId, account.status, account.freezeReason);
        this.freezeSubmitting.set(false);
      },
      error: error => {
        this.freezeError.set(error?.error?.details || error?.error?.message || 'Unable to unfreeze this account.');
        this.freezeSubmitting.set(false);
      }
    });
  }

  private updateAccountState(accountId: string, status: string, freezeReason: string | null): void {
    this.rows.update(rows => rows.map(row => row.accountId === accountId
      ? { ...row, accountStatus: status, freezeReason }
      : row));
    const selected = this.selectedUser();
    if (selected?.accountId === accountId) {
      this.selectedUser.set({ ...selected, accountStatus: status, freezeReason });
    }
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.clearSelection();
      this.loadDashboard(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.clearSelection();
      this.loadDashboard(this.page() + 1);
    }
  }

  toggleHoldingsTable(): void {
    this.holdingsTableCollapsed.set(!this.holdingsTableCollapsed());
  }

  toggleTradingPanel(): void {
    this.tradingPanelCollapsed.update(collapsed => !collapsed);
  }

  toggleDetailPanelFullscreen(): void {
    if (this.detailPanelFullscreen()) {
      this.exitDetailPanelFullscreen();
      return;
    }

    this.tradingPanelCollapsed.set(false);
    this.detailPanelFullscreen.set(true);

    const detailPanel = this.userDetailLayout?.nativeElement;
    if (detailPanel?.requestFullscreen) {
      void detailPanel.requestFullscreen().catch(() => undefined);
    }
  }

  startDetailPanelResize(event: PointerEvent): void {
    if (this.detailPanelFullscreen()) {
      return;
    }

    const splitter = event.currentTarget as HTMLElement;
    this.resizingDetailPanel = true;
    this.resizeStartY = event.clientY;
    this.resizeStartHeight = this.detailPanelHeight();
    this.resizeStartTopOffset = this.detailPanelTopOffset();
    splitter.setPointerCapture?.(event.pointerId);
    event.preventDefault();
  }

  @HostListener('document:pointermove', ['$event'])
  resizeDetailPanel(event: PointerEvent): void {
    if (!this.resizingDetailPanel) {
      return;
    }

    const requestedHeight = this.resizeStartHeight - (event.clientY - this.resizeStartY);
    const nextHeight = this.clamp(requestedHeight, 300, 900);
    this.detailPanelHeight.set(nextHeight);
    this.detailPanelTopOffset.set(this.resizeStartTopOffset + this.resizeStartHeight - nextHeight);
  }

  @HostListener('document:pointerup')
  stopDetailPanelResize(): void {
    this.resizingDetailPanel = false;
  }

  onDetailPanelResizerKeydown(event: KeyboardEvent): void {
    if (this.detailPanelFullscreen()) {
      return;
    }

    if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      const change = event.key === 'ArrowUp' ? 20 : -20;
      this.setDetailPanelHeight(this.detailPanelHeight() + change);
      event.preventDefault();
    } else if (event.key === 'Home') {
      this.setDetailPanelHeight(300);
      event.preventDefault();
    } else if (event.key === 'End') {
      this.setDetailPanelHeight(900);
      event.preventDefault();
    }
  }

  @HostListener('document:fullscreenchange')
  onFullscreenChange(): void {
    if (!document.fullscreenElement) {
      this.detailPanelFullscreen.set(false);
    }
  }

  private exitDetailPanelFullscreen(): void {
    this.detailPanelFullscreen.set(false);
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => undefined);
    }
  }

  private clamp(value: number, minimum: number, maximum: number): number {
    return Math.min(Math.max(value, minimum), maximum);
  }

  private setDetailPanelHeight(height: number): void {
    const nextHeight = this.clamp(height, 300, 900);
    const heightChange = nextHeight - this.detailPanelHeight();
    this.detailPanelHeight.set(nextHeight);
    this.detailPanelTopOffset.update(offset => offset - heightChange);
  }

  getUserName(user: AdminDashboardRow): string {
    return `${user.firstName ?? ''} ${user.lastName ?? ''}`.trim()
      || user.email
      || user.userId;
  }

  getCellValue(user: AdminDashboardRow, header: string): string | number {
    switch (header) {
      case 'Name': return this.getUserName(user);
      case 'Balance': return user.balance;
      case 'Trades': return user.tradeCount;
      case 'Created At': return formatDate(user.createdAt, 'MM/dd/yyyy', 'en-US');
      case 'Status': return user.accountStatus;
      case 'Portfolio Value': return user.portfolioValue;
      case 'Account Value': return user.balance + user.portfolioValue;
      case 'Profit': return user.allTimeProfit;
      default: return '';
    }
  }

  isMoneyColumn(header: string): boolean {
    return ['Account Value', 'Balance', 'Portfolio Value', 'Profit'].includes(header);
  }
}
