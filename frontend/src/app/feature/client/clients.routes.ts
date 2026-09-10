// clients.routes.ts
import { Routes } from '@angular/router';
import { ClientDashboardComponent } from './pages/dashboard/client-dashboard.component';
import { PortfolioComponent } from './pages/portfolio/client-portfolio.component';

export const CLIENT_ROUTES: Routes = [
  {
    path: '',
    component: ClientDashboardComponent, // acts as the layout shell
    children: [
      { path: 'portfolio', component: PortfolioComponent },
      // add more tabs here later: 'orders', 'holdings', 'alerts', etc.
    ]
  }
];