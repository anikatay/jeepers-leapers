import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const analystGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isLoggedIn()) {
    return router.createUrlTree(['/auth/login']);
  }

  if (authService.role === 'ROLE_ANALYST') {
    return true;
  }

  return router.createUrlTree([authService.role === 'ROLE_ADMIN' ? '/admin' : '/client']);
};
