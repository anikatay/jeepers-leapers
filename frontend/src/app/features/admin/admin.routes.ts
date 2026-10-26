import { Routes } from '@angular/router';
import { AnalystDashboardComponent } from '../analyst/pages/dashboard/analyst-dashboard.component';
import { AdminLayoutComponent } from './admin-layout.component';
import { AdminHomeComponent } from './pages/home/admin-home.component';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminLayoutComponent,
    children: [
      {
        path: '',
        component: AdminHomeComponent
      },
      {
        path: 'analytics',
        component: AnalystDashboardComponent
      }
    ]
  }
];