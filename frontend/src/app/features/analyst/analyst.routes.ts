import { Routes } from '@angular/router';
import { analystGuard } from '../../core/guards/analyst.guard';
import { AnalystLayoutComponent } from './analyst-layout.component';
import { AnalystDashboardComponent } from './pages/dashboard/analyst-dashboard.component';
import { MarketHeatmapComponent } from '../market-heatmap/market-heatmap.component';

export const ANALYST_ROUTES: Routes = [
  {
    path: '',
    component: AnalystLayoutComponent,
    canActivate: [analystGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      { path: 'dashboard', component: AnalystDashboardComponent },
      { path: 'heatmap', component: MarketHeatmapComponent }
    ]
  }
];