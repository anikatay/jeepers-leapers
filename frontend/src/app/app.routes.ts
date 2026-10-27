import { Routes } from '@angular/router';
import { Landing } from './landing/landing';
import { AuthShell, AuthSlot } from './auth-shell/auth-shell';
import { CLIENT_ROUTES } from './features/client/client.routes';
import { ADMIN_ROUTES } from './features/admin/admin.routes';
import { ANALYST_ROUTES } from './features/analyst/analyst.routes';
import { Login } from './login/login';
import { Register } from './register/register';
import { adminGuard } from './core/guards/admin.guard';
import { analystGuard } from './core/guards/analyst.guard';

export const routes: Routes = [
  {
    path: '',
    component: Landing,
    pathMatch: 'full'
  },
  {
    path: 'auth',
    component: AuthShell,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'login'
      },
      {
        path: 'login',
        component: AuthSlot
      },
      {
        path: 'register',
        component: AuthSlot
      }
    ]
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