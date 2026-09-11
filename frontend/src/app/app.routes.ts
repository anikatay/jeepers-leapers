import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'client', pathMatch: 'full' },
  {
    path: 'client',
    loadChildren: () => import('./feature/client/client.routes').then(m => m.CLIENT_ROUTES)
  },
  {
    path: 'admin',
    loadChildren: () => import('./feature/admin/admin.routes').then(m => m.ADMIN_ROUTES)
  }
];