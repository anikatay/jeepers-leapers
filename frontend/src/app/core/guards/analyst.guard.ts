import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const analystGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const role = authService.role;

  return role === 'ROLE_ANALYST' || role === 'ANALYST'
    ? true
    : router.createUrlTree(['/auth/login']);
};