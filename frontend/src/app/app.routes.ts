import { Routes } from '@angular/router';
import { Landing } from './landing/landing';
import { AuthShell, AuthSlot } from './auth-shell/auth-shell';
import { CLIENT_ROUTES } from './features/client/client.routes';
import { ADMIN_ROUTES } from './features/admin/admin.routes';
import { ANALYST_ROUTES } from './features/analyst/analyst.routes';
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