import { Component, OnInit, effect } from '@angular/core';
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
export class AdminUsersComponent implements OnInit{
    accounts;
    trades;
    searchTerm = ''; 

    accountIdToUser = new Map<string, { name: string; userId: string }>([
        ['d4000000-0000-0000-0000-000000000001', { name: 'Alice',   userId: 'a1000000-0000-0000-0000-000000000001' }],
        ['d4000000-0000-0000-0000-000000000002', { name: 'Bob',     userId: 'a1000000-0000-0000-0000-000000000002' }],
        ['d4000000-0000-0000-0000-000000000003', { name: 'Charlie', userId: 'a1000000-0000-0000-0000-000000000003' }],
      ]);
    // columns: { header: string; field: keyof Trade or keyof Account; }[] = [
    //     { header: 'Name', field: accountIdToUserId },
    //     { header: 'Balance', field: 'balance' },
    //     { header: 'Trades', field: trades[userId].length},
    //     { header: 'Current Portfolio Profit', field: calculateCurrentProfit() },
    //     { header: 'All-Time Profit', field: calculateAlltimeProfit() },
    //     { header: 'Online Status', field: 'status' },
    //   ];
    columns: { header: string }[] = [
        { header: 'Name' },
        { header: 'Balance' },
      ];
    


    constructor(private accountService: AccountService, private tradeService: TradesService) {
        this.accounts = this.accountService.allAccounts;
        this.trades = this.tradeService.trades;
    
        effect(() => {
          const accountsData = this.accounts();
          if (accountsData) {
            console.log('Accounts loaded:', accountsData);
          }
        });

        effect(() => {
            const tradesData = this.trades();
            if (tradesData) {
              console.log('Accounts loaded:', tradesData);
            }
          });
    }

    ngOnInit(){
        this.accountService.getAllAccounts();
        this.tradeService.getTrades('a1000000-0000-0000-0000-000000000001');
    }

    get knownAccounts() {
        const data = this.accounts();
        if (!data) return [];
        return data.filter(account => this.accountIdToUser.has(account.accountId));
      }

    getCellValue(account: any, header: string): string {
        switch (header) {
            case 'Name':
                return this.accountIdToUser.get(account.accountId)?.name ?? '';
            case 'Balance':
              return account.balance;
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