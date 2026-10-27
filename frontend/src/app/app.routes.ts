import { Routes } from '@angular/router';
import { Login } from './login/login';
import { Register } from './register/register';
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
    loadChildren: () => import('./features/client/client.routes').then(m => m.CLIENT_ROUTES)
  },
  {
    path: 'admin',
    canMatch: [adminGuard],
    loadChildren: () => import('./features/admin/admin.routes').then(m => m.ADMIN_ROUTES)
  },
  {
    path: 'analyst',
    canMatch: [analystGuard],
    loadChildren: () => import('./features/analyst/analyst.routes').then(m => m.ANALYST_ROUTES)
  },
  {
    path: '**',
    redirectTo: 'auth/login'
  }
];