import { Component, OnInit, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
// import { UsersService } from '../../../../core/services/users.service';
// import { User } from '../../../../core/models/user.model';
import { TradesService } from '../../../../core/services/trades.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.css'
})
export class AdminUsersComponent{
  columnsName = ['Name', 'Trades', 'Gains', 'Online Status'];
  allUsers = [ 
        { userId: 'a1000000-0000-0000-0000-000000000001', name: 'Alice', trades: '1', gains: '$100', onlineStatus: 'online' }, 
        { userId: 'a1000000-0000-0000-0000-000000000002', name: 'Bob', trades: '2', gains: '$200', onlineStatus: 'offline' },
        { userId: 'a1000000-0000-0000-0000-000000000003', name: 'Charlie', trades: '3', gains: '$300', onlineStatus: 'online' },
        { userId: 'a1000000-0000-0000-0000-000000000004', name: 'David', trades: '4', gains: '$400', onlineStatus: 'offline' },
        { userId: 'a1000000-0000-0000-0000-000000000005', name: 'Emma', trades: '5', gains: '$500', onlineStatus: 'online' } 
    ];

    searchTerm = ''; 
    selectedUser: { 
        userId: string; 
        name: string; 
        trades: string; 
        gains: string; 
        onlineStatus: string; 
    } | null = null;


  get filteredUsers() {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) return this.allUsers;

    return this.allUsers
      .filter(u => u.name.toLowerCase().includes(term))
      .sort((a, b) => {
        const aStarts = a.name.toLowerCase().startsWith(term);
        const bStarts = b.name.toLowerCase().startsWith(term);
        if (aStarts && !bStarts) return -1;
        if (!aStarts && bStarts) return 1;
        return a.name.localeCompare(b.name);
      });
  }

  selectUser(user: typeof this.allUsers[number]) {
    this.selectedUser = user;
  }

  clearSelection() {
    this.selectedUser = null;
  }
}