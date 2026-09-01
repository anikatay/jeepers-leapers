import { Routes } from '@angular/router';
import { ClientDashboardComponent } from './client-dashboard/client-dashboard.component';

export const routes: Routes = [
  {
    path: '',
    component: ClientDashboardComponent
  },
  {
    path: '**',
    redirectTo: ''
  }
];
