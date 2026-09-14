// clients.routes.ts
import { Routes } from '@angular/router';
import { ClientHomeComponent } from './pages/home/client-home.component';
import { ClientPortfolioComponent } from './pages/portfolio/client-portfolio.component';

export const CLIENT_ROUTES: Routes = [
  {
    path: '',
    component: ClientHomeComponent,
    children: [
      { path: 'portfolio', component: ClientPortfolioComponent },
      // 'orders' later, same pattern
    ]
  }
];