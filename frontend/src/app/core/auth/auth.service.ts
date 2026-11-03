import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, finalize, firstValueFrom, map, shareReplay, tap } from 'rxjs';

import { AuthResponse, AuthUser } from './auth.models';

export interface RegisterRequest {
  fullName: string;
  email: string;
  phone: string | null;
  password: string;
}

/** Thrown when the browser has no session cookie (refresh answered 204). */
export class NoSessionError extends Error {
  constructor() {
    super('No session');
  }
}

/**
 * Holds the session. The access token lives only in memory (never in localStorage);
 * the refresh token is an HttpOnly cookie the browser sends to /api/auth/* only.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly currentUser = signal<AuthUser | null>(null);
  private accessToken: string | null = null;
  private refreshInFlight: Observable<AuthResponse> | null = null;

  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);
  readonly isAdmin = computed(() => this.currentUser()?.roles.includes('ROLE_ADMIN') ?? false);
  readonly firstName = computed(() => this.currentUser()?.fullName.trim().split(/\s+/)[0] ?? '');

  token(): string | null {
    return this.accessToken;
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password }, { withCredentials: true })
      .pipe(map((response) => this.apply(response)));
  }

  register(request: RegisterRequest): Observable<AuthUser> {
    return this.http
      .post<AuthResponse>('/api/auth/register', request, { withCredentials: true })
      .pipe(map((response) => this.apply(response)));
  }

  /** Always succeeds from the user's point of view; the server never reveals whether the email exists. */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>('/api/auth/forgot-password', { email });
  }

  resetPassword(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/auth/reset-password', { token, newPassword });
  }

  /** One refresh at a time: parallel 401s share the same request. */
  refresh(): Observable<AuthResponse> {
    if (!this.refreshInFlight) {
      this.refreshInFlight = this.http
        .post<AuthResponse | null>('/api/auth/refresh', null, { withCredentials: true })
        .pipe(
          map((response) => {
            if (!response) {
              throw new NoSessionError();
            }
            return response;
          }),
          tap((response) => this.apply(response)),
          finalize(() => (this.refreshInFlight = null)),
          shareReplay(1),
        );
    }
    return this.refreshInFlight;
  }

  /** Called once at start-up: restores the session from the refresh cookie if there is one. */
  async restoreSession(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
    } catch {
      this.clear();
    }
  }

  logout(redirectTo = '/login'): void {
    this.http.post('/api/auth/logout', null, { withCredentials: true }).subscribe({ error: () => undefined });
    this.clear();
    void this.router.navigateByUrl(redirectTo);
  }

  /** The refresh token was rejected: drop the session and ask the user to log in again. */
  sessionExpired(returnUrl?: string): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { returnUrl, reason: 'expired' } });
  }

  private apply(response: AuthResponse): AuthUser {
    this.accessToken = response.accessToken;
    this.currentUser.set(response.user);
    return response.user;
  }

  private clear(): void {
    this.accessToken = null;
    this.currentUser.set(null);
  }
}
