import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';

import { AuthService } from './auth.service';

const AUTH_ENDPOINTS = ['/api/auth/login', '/api/auth/register', '/api/auth/refresh', '/api/auth/logout',
  '/api/auth/forgot-password', '/api/auth/reset-password'];

export function isAuthEndpoint(url: string): boolean {
  return AUTH_ENDPOINTS.some((endpoint) => url.startsWith(endpoint));
}

/**
 * Adds the bearer token to API calls. On a 401 for a logged-in user it refreshes the
 * access token once and retries; if refreshing fails the session has expired.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith('/api/') || isAuthEndpoint(req.url)) {
    return next(req);
  }
  const auth = inject(AuthService);
  const router = inject(Router);
  const withToken = (request: HttpRequest<unknown>, token: string | null) =>
    token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;

  return next(withToken(req, auth.token())).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse) || error.status !== 401 || !auth.isLoggedIn()) {
        return throwError(() => error);
      }
      return auth.refresh().pipe(
        catchError(() => {
          auth.sessionExpired(router.url);
          return throwError(() => error);
        }),
        switchMap((response) => next(withToken(req, response.accessToken))),
      );
    }),
  );
};
