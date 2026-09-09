import { Routes } from '@angular/router';
import { ClientDashboardComponent } from './feature/client/pages/dashboard/client-dashboard.component';

export const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: ClientDashboardComponent }
];
