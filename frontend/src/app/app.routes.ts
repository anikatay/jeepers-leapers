import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'client', pathMatch: 'full' },
  {
    path: 'client',
    loadChildren: () => import('./feature/client/clients.routes').then(m => m.CLIENT_ROUTES)
  }
];