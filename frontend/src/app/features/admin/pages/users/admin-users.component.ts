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