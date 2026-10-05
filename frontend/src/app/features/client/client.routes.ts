// clients.routes.ts
import { Routes } from '@angular/router';
import { ClientLayoutComponent } from './client-layout.component';
import { ClientHomeComponent } from './pages/home/client-home.component';
import { ClientPortfolioComponent } from './pages/portfolio/client-portfolio.component';
import { ClientTradesComponent } from './pages/trades/client-trades.component';

export const CLIENT_ROUTES: Routes = [
  {
    path: '',
    component: ClientLayoutComponent,
    children: [
      { path: '', component: ClientHomeComponent },
      { path: 'portfolio', component: ClientPortfolioComponent },
      { path: 'trades', component: ClientTradesComponent },
    ]
  }
];
