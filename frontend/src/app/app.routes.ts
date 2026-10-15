import { Routes } from '@angular/router';
import { Login } from './login/login';
import { CLIENT_ROUTES } from './features/client/client.routes';
import { ADMIN_ROUTES } from './features/admin/admin.routes';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'auth/login',
    pathMatch: 'full'
  },
  {
    path: 'auth/login',
    component: Login
  },
  {
    path: 'client',
    children: CLIENT_ROUTES
  },
  {
    path: 'admin',
    children: ADMIN_ROUTES
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];