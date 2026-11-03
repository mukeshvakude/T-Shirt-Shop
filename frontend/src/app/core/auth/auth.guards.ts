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

/** Pages for signed-in customers (account, orders): otherwise go to login and come back. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isLoggedIn() ? true : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Login, register and password pages: signed-in users go to the portal (admins) or the shop. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (!auth.isLoggedIn()) {
    return true;
  }
  return inject(Router).createUrlTree([auth.isAdmin() ? '/admin' : '/']);
};

/** Only same-site paths are allowed as redirect targets (prevents open redirects). */
export function safeReturnUrl(url: string | null | undefined, fallback: string): string {
  return url && url.startsWith('/') && !url.startsWith('//') ? url : fallback;
}
