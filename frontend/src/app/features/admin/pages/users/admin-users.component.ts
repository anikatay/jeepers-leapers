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
  allUsers = [ 
        { userId: 'a1000000-0000-0000-0000-000000000001', name: 'Alice', email: 'alice@example.com' }, 
        { userId: 'a1000000-0000-0000-0000-000000000002', name: 'Bob', email: 'bob@example.com' },
        { userId: 'a1000000-0000-0000-0000-000000000003', name: 'Charlie', email: 'charlie@example.com' },
        { userId: 'a1000000-0000-0000-0000-000000000004', name: 'David', email: 'david@example.com' },
        { userId: 'a1000000-0000-0000-0000-000000000005', name: 'Emma', email: 'emma@example.com' } 
    ];

    searchTerm = ''; 
    selectedUser: { 
        userId: string; 
        name: string; 
        email: string; 
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