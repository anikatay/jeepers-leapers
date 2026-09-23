import { Component, OnInit, computed, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountService } from '../../../../core/services/accounts.service';
import { Trade } from '../../../../core/models/trade.model';
import { Account } from '../../../../core/models/account.model';
import { TradesService } from '../../../../core/services/trades.service';
import { InstrumentService } from '../../../../core/services/instrument.model';
import { Instrument } from '../../../../core/models/instrument.model';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css'
})
export class AdminUsersComponent implements OnInit{
    accounts = signal<Account[]>([]);
    instruments = signal<Instrument | null>(null);
    tradesByUserId = signal<Map<string, Trade[]>>(new Map());
    searchTerm = ''; 
    pendingUserIds = new Set<string>(); 
    columns: { header: string }[] = [
        { header: 'Name' },
        { header: 'Balance' },
        { header: 'Trade Count' },
        { header: 'Profit' },
        { header: 'Status' }, 
      ];

    userData = computed(() => {
      const accountsData = this.accounts() || [];
      const tradesMap = this.tradesByUserId(); // Read the signal
      return accountsData.map(account => ({
        accountId: account.accountId,
        userId: account.userId,
        name: this.getUserName(account.userId),
        balance: account.balance,
        tradeCount: this.loadAndGetTradeCount(account.userId),
        profit: this.getProfit(account.userId),
        status: account.status
      }));

    });
    


    constructor(private accountService: AccountService, private tradeService: TradesService, private instrumentService: InstrumentService) {
        effect(() => {
          const accountsData = this.accountService.allAccounts();
          if (accountsData) {
            this.accounts.set(accountsData);
            console.log('Accounts loaded:', accountsData);
            
            // Load trades sequentially after accounts load
            this.loadTradesSequentially(accountsData);
          }
        });

        effect(() => {
          const instrumentsData = this.instrumentService.instruments();
          if (instrumentsData) {
            this.instruments.set(instrumentsData);
            console.log('Instruments loaded:', instrumentsData);
          }
        });

    }

    ngOnInit(){
      this.accountService.getAllAccounts();
    }

    private loadTradesSequentially(accounts: Account[]): void {
      if (accounts.length === 0) return;

      let index = 0;

      const loadNext = () => {
        if (index >= accounts.length) {
          console.log('✓ All trades loaded');
          return;
        }

        const userId = accounts[index].userId;
        const currentMap = this.tradesByUserId();

        if (currentMap.has(userId)) {
          console.log('✓ Already have trades for', userId);
          index++;
          loadNext();
          return;
        }

        console.log('→ Loading trades for', userId);

        // Track this pending request
        this.pendingUserIds.add(userId);

        // Request trades for this user
        this.tradeService.getTrades(userId);

        // Wait for trades signal to update
        let attempts = 0;
        const pollInterval = setInterval(() => {
          const tradesData = this.tradeService.trades();

          if (tradesData && tradesData.length > 0) {
            // Update the map and trigger the signal
            const updatedMap = new Map(this.tradesByUserId());
            updatedMap.set(userId, tradesData);
            this.tradesByUserId.set(updatedMap); // Update signal
            this.pendingUserIds.delete(userId);
            console.log('✓ Stored', tradesData.length, 'trades for', userId);
            clearInterval(pollInterval);

            // Move to next user
            index++;
            setTimeout(loadNext, 300);
          } else if (attempts > 50) { // 50 * 100ms = 5 second timeout
            console.warn('⚠ Timeout loading trades for', userId);
            clearInterval(pollInterval);
            this.pendingUserIds.delete(userId);
            index++;
            loadNext();
          }

          attempts++;
        }, 100);
      };

      loadNext();
    }
    
    getUserName(userId: string): string {
      if(userId == "a1000000-0000-0000-0000-000000000001")
        return "Alice";
      if(userId == "a1000000-0000-0000-0000-000000000002")
        return "Bob";
      if(userId == "a1000000-0000-0000-0000-000000000003")
        return "Charlie";
      return '';
    }

    getProfit(userId: string): number {
      const userTrades = this.tradesByUserId().get(userId) || [];
      if (userTrades.length === 0) return 0;

      let realizedProfit = 0;
      let unrealizedProfit = 0;
      const buyTradesByTicker = new Map<string, any[]>(); // Track BUY trades by ticker
      const soldQuantityByTicker = new Map<string, number>(); // Track qty sold per ticker

      // First pass: organize BUY trades by ticker and calculate realized profit from SELLs
      for (const trade of userTrades) {
        if (trade.side === 'BUY') {
          if (!buyTradesByTicker.has(trade.ticker)) {
            buyTradesByTicker.set(trade.ticker, []);
          }
          buyTradesByTicker.get(trade.ticker)!.push(trade);
        }
      }

      // Second pass: process SELL trades and match with BUYs
      for (const trade of userTrades) {
        if (trade.side === 'SELL') {
          // Find matching BUY trades for this ticker
          const matchingBuys = buyTradesByTicker.get(trade.ticker) || [];
          
          // Find the BUY that was executed before this SELL
          const matchingBuy = matchingBuys.find(
            buy => new Date(buy.executedAt) < new Date(trade.executedAt)
          );

          if (matchingBuy) {
            // Calculate realized profit: SELL value - BUY value
            realizedProfit += trade.tradeValue - matchingBuy.tradeValue;
            soldQuantityByTicker.set(
              trade.ticker, 
              (soldQuantityByTicker.get(trade.ticker) || 0) + trade.quantity
            );
          }
        }
      }

      // Third pass: calculate unrealized profit from unsold BUY holdings
      for (const [ticker, buyTrades] of buyTradesByTicker.entries()) {
        const totalBought = buyTrades.reduce((sum, t) => sum + t.quantity, 0);
        const totalSold = soldQuantityByTicker.get(ticker) || 0;
        const remainingQty = totalBought - totalSold;

        if (remainingQty > 0) {
          // Get current price for this ticker
          const currentPrice = this.getInstrumentPrice(ticker);
          if (currentPrice) {
            const totalBuyValue = buyTrades.reduce((sum, t) => sum + t.tradeValue, 0);
            const currentValue = currentPrice * remainingQty;
            unrealizedProfit += currentValue - totalBuyValue;
          }
        }
      }

      return realizedProfit + unrealizedProfit;
    }

    private getInstrumentPrice(ticker: string): number | null {
      const instrumentsList = this.instruments();
      
      if (Array.isArray(instrumentsList)) {
        const instrument = instrumentsList.find((i: any) => i.ticker === ticker);
        return instrument?.currentPrice || null;
      }
      
      // If instruments is an object, try to access it as a keyed object
      if (instrumentsList && typeof instrumentsList === 'object') {
        const instrument = (instrumentsList as any)[ticker];
        if (instrument && typeof instrument === 'object' && 'currentPrice' in instrument) {
          return instrument.currentPrice as number;
        }
      }

      return null;
    }

    loadAndGetTradeCount(userId: string): number {
      const tradesMap = this.tradesByUserId();
      return tradesMap.get(userId)?.length || 0;
    }

    getCellValue(user: any, header: string): any {
        switch (header) {
            case 'Name':
                return user.name;
            case 'Balance':
              return user.balance;
            case 'Trade Count':
              return user.tradeCount;
            case 'Profit':
              return user.profit;
            case 'Status':
              return user.status;
            default:
              return '';
          }
    }

//   get filteredUsers() {
//     const term = this.searchTerm.trim().toLowerCase();
//     if (!term) return this.allUsers;

//     return this.allUsers
//       .filter(u => u.name.toLowerCase().includes(term))
//       .sort((a, b) => {
//         const aStarts = a.name.toLowerCase().startsWith(term);
//         const bStarts = b.name.toLowerCase().startsWith(term);
//         if (aStarts && !bStarts) return -1;
//         if (!aStarts && bStarts) return 1;
//         return a.name.localeCompare(b.name);
//       });
//   }

//   selectUser(user: typeof this.allUsers[number]) {
//     this.selectedUser = user;
//   }

//   clearSelection() {
//     this.selectedUser = null;
//   }

//   displayUsers(){

//   }

}