import { Routes } from '@angular/router';
import { Login } from './login/login';
import { Register } from './register/register';
import { CLIENT_ROUTES } from './features/client/client.routes';
import { ADMIN_ROUTES } from './features/admin/admin.routes';
import { ANALYST_ROUTES } from './features/analyst/analyst.routes';

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
    children: ADMIN_ROUTES
  },
  {
    path: 'analyst',
    children: ANALYST_ROUTES
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];