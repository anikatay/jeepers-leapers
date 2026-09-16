// clients.routes.ts
import { Routes } from '@angular/router';
import { AdminHomeComponent } from './pages/home/admin-home.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminHomeComponent // acts as the layout shell

  }
];