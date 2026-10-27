import { Routes } from '@angular/router';
import { analystGuard } from '../../core/guards/analyst.guard';
import { AnalystLayoutComponent } from './analyst-layout.component';

export const ANALYST_ROUTES: Routes = [
  {
    path: '',
    component: AnalystLayoutComponent,
    canActivate: [analystGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () => import('./pages/dashboard/analyst-dashboard.component').then(m => m.AnalystDashboardComponent)
      },
      {
        path: 'heatmap',
        loadComponent: () => import('../market-heatmap/market-heatmap.component').then(m => m.MarketHeatmapComponent)
      }
    ]
  }
];