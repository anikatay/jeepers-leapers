import { Routes } from '@angular/router';
import { CLIENT_ROUTES } from './features/client/client.routes';
import { ADMIN_ROUTES } from './features/admin/admin.routes';

export const routes: Routes = [
  { path: '', redirectTo: 'client', pathMatch: 'full' },
  { path: 'client', children: CLIENT_ROUTES },
  { path: 'admin', children: ADMIN_ROUTES }
];
