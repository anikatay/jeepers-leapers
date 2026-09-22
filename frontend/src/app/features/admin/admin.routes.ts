// clients.routes.ts
import { Routes } from '@angular/router';
import { AdminHomeComponent } from './pages/home/admin-home.component';
import { AdminUsersComponent } from './pages/users/admin-users.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminHomeComponent, // acts as the layout shell
    children: [
      { path: 'users', component: AdminUsersComponent }
      // 'orders' later, same pattern
    ]

  }
];