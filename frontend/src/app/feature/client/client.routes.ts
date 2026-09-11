// clients.routes.ts
import { Routes } from '@angular/router';
import { ClientHomeComponent } from './pages/home/client-home.component';
import { ClientPortfolioComponent } from './pages/portfolio/client-portfolio.component';

export const CLIENT_ROUTES: Routes = [
  {
    path: '',
    component: ClientHomeComponent, // acts as the layout shell
    children: [
      { path: 'portfolio', component: ClientPortfolioComponent },
      // add more tabs here later: 'orders', 'holdings', 'alerts', etc.
    ]
  }
];