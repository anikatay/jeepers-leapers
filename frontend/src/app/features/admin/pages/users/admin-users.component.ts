import { Component, OnInit, computed, effect, signal } from '@angular/core';
import { CommonModule, formatDate } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccountService } from '../../../../core/services/accounts.service';
import { Trade } from '../../../../core/models/trade.model';
import { Account } from '../../../../core/models/account.model';
import { TradesService } from '../../../../core/services/trades.service';
import { ClientPortfolioComponent } from '../../../client/pages/portfolio/client-portfolio.component';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css'
})
export class AdminUsersComponent{
    accounts = signal<Account[]>([]);
    tradesByAccountId = signal<Map<string, Trade[]>>(new Map());
    accountValue = signal<Map<string, number>>(new Map());
    allTimeProfitByAccountId = signal<Map<string, number>>(new Map());
    portfolioValueByAccountId = signal<Map<string, number>>(new Map());
    selectedUser = signal<any>(null);
    searchTerm = ''; 
    pendingUserIds = new Set<string>(); 
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

    userData = computed(() => {
      const accountsData = this.accounts() || [];

      return accountsData.map(account => ({
        accountId: account.accountId,
        name: this.getUserName(account.userId),
        balance: account.balance,
        tradeCount: this.tradesByAccountId().get(account.accountId)?.length || 0,
        createdAt: account.createdAt,
        status: account.status,
        portfolioValue: this.portfolioValueByAccountId().get(account.accountId) || 0,
        accountValue: (this.portfolioValueByAccountId().get(account.accountId) || 0) + account.balance,
        allTimeProfit: this.allTimeProfitByAccountId().get(account.accountId) || 0
      }));

    });


    constructor(private accountService: AccountService, private tradeService: TradesService) {
        // Trigger the API call
        this.accountService.getAllAccounts();
        
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
      let i = 0;  // Index counter (like a for loop)
  
      // Define a function that calls itself
      const loadNext = () => {
        // Base case: stop when all accounts processed
        if (i >= accounts.length) {
          console.log('✓ All trades loaded');
          return;  // Stop recursion
        }

        // Get current user
        const accountId = accounts[i].accountId;
        console.log('accountId:', accountId, 'userId:', accounts[i].userId);
        this.tradeService.getTrades(accountId); // Fetch trades
        this.tradeService.calculatePortfolioMetrics(accountId);

        // Poll for data to arrive
        let attempts = 0;
        const checkInterval = setInterval(() => {
          const tradesData = this.tradeService.trades();
          const metricsData = this.tradeService.portfolioMetrics();
          
          if (tradesData && tradesData.length > 0 && metricsData) {
            // Both data arrived! Store it
            const updatedTradesMap = new Map(this.tradesByAccountId());
            updatedTradesMap.set(accountId, tradesData);
            this.tradesByAccountId.set(updatedTradesMap);

            const updatedPortfolioMap = new Map(this.portfolioValueByAccountId());
            updatedPortfolioMap.set(accountId, metricsData.currentValue);
            this.portfolioValueByAccountId.set(updatedPortfolioMap);

            const updatedAllTimeProfitMap = new Map(this.allTimeProfitByAccountId());
            updatedAllTimeProfitMap.set(accountId, metricsData.allTimeProfit);
            this.allTimeProfitByAccountId.set(updatedAllTimeProfitMap);
            
            clearInterval(checkInterval);
            
            i++;  // Move to next user
            loadNext();  // 🔄 Recursively call itself for next user
          } else if (attempts++ > 50) {
            // Timeout after 5 seconds
            clearInterval(checkInterval);
            i++;  // Move to next user anyway
            loadNext();  // 🔄 Recursively call itself for next user
          }
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

    getCellValue(user: any, header: string): any {
        switch (header) {
            case 'Name':
                return user.name;
            case 'Balance':
              return user.balance;
            case 'Trades':
              return user.tradeCount;
            case 'Created At':
              return formatDate(user.createdAt, 'MM/dd/yyyy', 'en-US');
            case 'Status':
              return user.status;
            case 'Portfolio Value':
              return user.portfolioValue;
            case 'Account Value':
              return user.accountValue;
            case 'Profit':
              return user.allTimeProfit;
            default:
              return '';
          }
    }

    // Computed signal to get the selected user's trades
    selectedUserTrades = computed(() => {
      const selected = this.selectedUser();
      if (!selected) return [];
      return this.tradesByAccountId().get(selected.accountId) || [];
    });

    selectUser(user: any) {
      this.selectedUser.set(user);  // Set the clicked user
    }

    clearSelection() {
      this.selectedUser.set(null);
    }

}