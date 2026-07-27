import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { Auth } from './auth';

export const mustSetPasswordGuard: CanActivateChildFn = (route, state) => {
  const authService = inject(Auth);
  const router = inject(Router);

  if (authService.hasPassword()) {
    return true;
  }

  router.navigate(['/set-password']);
  return false;
};