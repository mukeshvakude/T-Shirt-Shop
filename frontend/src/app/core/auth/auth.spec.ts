import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';

import { AuthResponse } from './auth.models';
import { adminGuard, guestGuard } from './auth.guards';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

function authResponse(token: string, roles: ('ROLE_CUSTOMER' | 'ROLE_ADMIN')[]): AuthResponse {
  return {
    accessToken: token,
    tokenType: 'Bearer',
    expiresIn: 900,
    expiresAt: '2026-11-02T12:15:00Z',
    user: { id: 1, email: 'admin@test.local', fullName: 'Test Admin', emailVerified: true, roles, createdAt: '' },
  };
}

describe('authentication', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  function login(roles: ('ROLE_CUSTOMER' | 'ROLE_ADMIN')[] = ['ROLE_ADMIN']): void {
    auth.login('admin@test.local', 'secret').subscribe();
    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.withCredentials).toBe(true);
    req.flush(authResponse('token-1', roles));
  }

  it('keeps the user and token in memory after login', () => {
    login();
    expect(auth.isLoggedIn()).toBe(true);
    expect(auth.isAdmin()).toBe(true);
    expect(auth.token()).toBe('token-1');
  });

  it('adds the bearer token to API calls but not to auth endpoints or other hosts', () => {
    login();
    http.get('/api/admin/products').subscribe();
    expect(httpMock.expectOne('/api/admin/products').request.headers.get('Authorization')).toBe('Bearer token-1');

    http.get('https://example.com/data').subscribe();
    expect(httpMock.expectOne('https://example.com/data').request.headers.has('Authorization')).toBe(false);
  });

  it('refreshes once on 401 and retries with the new token, sharing one refresh for parallel calls', () => {
    login();
    const results: unknown[] = [];
    http.get('/api/admin/products').subscribe((r) => results.push(r));
    http.get('/api/admin/categories').subscribe((r) => results.push(r));

    httpMock.expectOne('/api/admin/products').flush({}, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/admin/categories').flush({}, { status: 401, statusText: 'Unauthorized' });

    const refresh = httpMock.expectOne('/api/auth/refresh');
    expect(refresh.request.withCredentials).toBe(true);
    refresh.flush(authResponse('token-2', ['ROLE_ADMIN']));

    const retried = httpMock.match((r) => r.url.startsWith('/api/admin/'));
    expect(retried.length).toBe(2);
    retried.forEach((r) => {
      expect(r.request.headers.get('Authorization')).toBe('Bearer token-2');
      r.flush(['ok']);
    });
    expect(results.length).toBe(2);
  });

  it('ends the session and goes to login when the refresh token is rejected', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    login();
    let failed = false;
    http.get('/api/admin/products').subscribe({ error: () => (failed = true) });

    httpMock.expectOne('/api/admin/products').flush({}, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectOne('/api/auth/refresh').flush({ code: 'INVALID_REFRESH_TOKEN' }, { status: 401, statusText: 'Unauthorized' });

    expect(failed).toBe(true);
    expect(auth.isLoggedIn()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.objectContaining({
      queryParams: expect.objectContaining({ reason: 'expired' }),
    }));
  });

  it('treats a 204 from refresh as "no session" without errors', async () => {
    const restored = auth.restoreSession();
    httpMock.expectOne('/api/auth/refresh').flush(null, { status: 204, statusText: 'No Content' });
    await restored;
    expect(auth.isLoggedIn()).toBe(false);
    expect(auth.token()).toBeNull();
  });

  it('sends signed-in customers away from guest pages to the shop', () => {
    const route = {} as ActivatedRouteSnapshot;
    const state = { url: '/login' } as RouterStateSnapshot;
    expect(TestBed.runInInjectionContext(() => guestGuard(route, state))).toBe(true);
    login(['ROLE_CUSTOMER']);
    expect((TestBed.runInInjectionContext(() => guestGuard(route, state)) as UrlTree).toString()).toBe('/');
    expect(auth.firstName()).toBe('Test');
  });

  it('does not try to refresh when nobody is logged in', () => {
    let status = 0;
    http.get('/api/admin/products').subscribe({ error: (e) => (status = e.status) });
    httpMock.expectOne('/api/admin/products').flush({}, { status: 401, statusText: 'Unauthorized' });
    httpMock.expectNone('/api/auth/refresh');
    expect(status).toBe(401);
  });

  it('restores a session from the refresh cookie, or stays logged out', async () => {
    const restored = auth.restoreSession();
    httpMock.expectOne('/api/auth/refresh').flush(authResponse('token-3', ['ROLE_ADMIN']));
    await restored;
    expect(auth.token()).toBe('token-3');

    const second = TestBed.inject(AuthService);
    const failed = second.restoreSession();
    httpMock.expectOne('/api/auth/refresh').flush({}, { status: 401, statusText: 'Unauthorized' });
    await failed;
    expect(second.isLoggedIn()).toBe(false);
  });

  it('guards admin routes by role', () => {
    const state = { url: '/admin/products' } as RouterStateSnapshot;
    const route = {} as ActivatedRouteSnapshot;
    const run = () => TestBed.runInInjectionContext(() => adminGuard(route, state));

    const anonymous = run() as UrlTree;
    expect(anonymous.toString()).toBe('/login?returnUrl=%2Fadmin%2Fproducts');

    login(['ROLE_CUSTOMER']);
    expect((run() as UrlTree).queryParams['reason']).toBe('forbidden');

    login(['ROLE_ADMIN', 'ROLE_CUSTOMER']);
    expect(run()).toBe(true);
    expect((TestBed.runInInjectionContext(() => guestGuard(route, state)) as UrlTree).toString()).toBe('/admin');
  });
});
