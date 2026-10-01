import { Component, OnInit, computed, effect, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountService } from '../../../../core/services/accounts.service';
import { Trade } from '../../../../core/models/trade.model';
import { Account } from '../../../../core/models/account.model';
import { TradesService } from '../../../../core/services/trades.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css'
})
export class AdminUsersComponent{
    accounts = signal<Account[]>([]);
    tradesByUserId = signal<Map<string, Trade[]>>(new Map());
    searchTerm = ''; 
    pendingUserIds = new Set<string>(); 
    columns: { header: string }[] = [
        { header: 'Name' },
        { header: 'Balance' },
        { header: 'Trade Count' },
        { header: 'Created At'},
        { header: 'Status' }, 
      ];

    userData = computed(() => {
      const accountsData = this.accounts() || [];

      return accountsData.map(account => ({
        accountId: account.accountId,
        userId: account.userId,
        name: this.getUserName(account.userId),
        balance: account.balance,
        tradeCount: this.loadAndGetTradeCount(account.userId),
        createdAt: account.createdAt,
        status: account.status
      }));

    });


    constructor(private accountService: AccountService, private tradeService: TradesService) {
        effect(() => {
          const accountsData = this.accountService.allAccounts();
          if (accountsData) {
            this.accounts.set(accountsData);
            console.log('Accounts loaded:', accountsData);
            
            // Load trades sequentially after accounts load
            this.loadTradesSequentially(accountsData);
          }
        });

      
    }

    loadTradesSequentially(accounts: Account[]): void {
      let i = 0;
      for (i = 0; i < accounts.length; i++) {

        //call the trades service for each userID
        const userId = accounts[i].userId;
        this.tradeService.getTrades(userId);

        //store the trades in a map with its user_id as the key
        const updatedMap = new Map(this.tradesByUserId());
        const tradesData = this.tradeService.trades();
        if(tradesData){
            updatedMap.set(userId, tradesData);
            this.tradesByUserId.set(updatedMap);
        }

      }
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
            case 'Created At':
              return user.createdAt;
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