import { Component, OnDestroy, OnInit, signal } from '@angular/core';
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
  readonly pageSize = 25;
  readonly holdingsTableCollapsed = signal(false);
  readonly tradesTableCollapsed = signal(false);

  searchTerm = '';
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
  private readonly destroy = new Subject<void>();
  private readonly loadedTradeAccountIds = new Set<string>();

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

    this.loadDashboard();
  }

  ngOnDestroy(): void {
    this.destroy.next();
    this.destroy.complete();
    this.searchChanges.complete();
  }

  onSearchChanged(): void {
    this.searchChanges.next(this.searchTerm);
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
    this.selectedUser.set(user);
    this.tradesError.set(null);
    this.freezeError.set(null);
    this.freezeReason = '';

    if (previousSelection?.accountId === user.accountId && this.loadedTradeAccountIds.has(user.accountId)) {
      return;
    }

    this.selectedUserTrades.set([]);
    this.loadingTrades.set(true);
    this.adminDashboardService.getAccountTrades(user.accountId).pipe(
      takeUntil(this.destroy),
      catchError(() => {
        this.tradesError.set('Unable to load trades for this account.');
        return of(null);
      })
    ).subscribe(trades => {
      if (!trades) {
        this.loadingTrades.set(false);
        return;
      }
      this.loadedTradeAccountIds.add(user.accountId);
      if (this.selectedUser()?.accountId === user.accountId) {
        this.selectedUserTrades.set(trades);
        this.loadingTrades.set(false);
      }
    });
  }

  clearSelection(): void {
    this.selectedUser.set(null);
    this.selectedUserTrades.set([]);
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

  toggleTradesTable(): void {
    this.tradesTableCollapsed.set(!this.tradesTableCollapsed());
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
}
