import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Admin pages: requires ROLE_ADMIN, otherwise sends the user to the login page. */
export const adminGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  if (auth.isAdmin()) {
    return true;
  }
  const queryParams: Record<string, string> = { returnUrl: state.url };
  if (auth.isLoggedIn()) {
    queryParams['reason'] = 'forbidden';
  }
  return inject(Router).createUrlTree(['/login'], { queryParams });
};

/** Login page: admins who are already logged in go straight to the portal. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAdmin() ? inject(Router).createUrlTree(['/admin']) : true;
};
