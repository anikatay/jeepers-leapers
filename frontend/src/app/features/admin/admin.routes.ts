import { Routes } from '@angular/router';
import { AdminLayoutComponent } from './admin-layout.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/home/admin-home.component').then(m => m.AdminHomeComponent)
      },
      {
        path: 'analytics',
        loadComponent: () => import('../analyst/pages/dashboard/analyst-dashboard.component').then(m => m.AnalystDashboardComponent)
      }
    ]
  }
];