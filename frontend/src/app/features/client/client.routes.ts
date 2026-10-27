// clients.routes.ts
import { Routes } from '@angular/router';
import { ClientLayoutComponent } from './client-layout.component';

export const CLIENT_ROUTES: Routes = [
  {
    path: '',
    component: ClientLayoutComponent,
    children: [
      {
        path: '',
        loadComponent: () => import('./pages/home/client-home.component').then(m => m.ClientHomeComponent)
      },
      {
        path: 'portfolio',
        loadComponent: () => import('./pages/portfolio/client-portfolio.component').then(m => m.ClientPortfolioComponent)
      },
      {
        path: 'trades',
        loadComponent: () => import('./pages/trades/client-trades.component').then(m => m.ClientTradesComponent)
      },
      {
        path: 'stock/:ticker',
        loadComponent: () => import('./pages/stock-details/stock-details.component').then(m => m.StockDetailsComponent)
      },
      {
        path: 'heatmap',
        loadComponent: () => import('../market-heatmap/market-heatmap.component').then(m => m.MarketHeatmapComponent)
      },
    ]
  }
];
