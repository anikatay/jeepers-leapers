import { Routes } from '@angular/router';
import { Login } from './login/login';
import { Register } from './register/register';
import { CLIENT_ROUTES } from './features/client/client.routes';
import { ADMIN_ROUTES } from './features/admin/admin.routes';
import { ANALYST_ROUTES } from './features/analyst/analyst.routes';
import { adminGuard } from './core/guards/admin.guard';
import { analystGuard } from './core/guards/analyst.guard';

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
    path: 'auth/register',
    component: Register
  },
  {
    path: 'client',
    children: CLIENT_ROUTES
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    children: ADMIN_ROUTES
  },
  {
    path: 'analyst',
    canActivate: [analystGuard],
    children: ANALYST_ROUTES
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];