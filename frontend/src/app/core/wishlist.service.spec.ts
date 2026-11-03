import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from './auth/auth.service';
import { WishlistService } from './wishlist.service';

describe('WishlistService', () => {
  let httpMock: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  function signIn(): void {
    auth.login('kavya@example.com', 'Secret123').subscribe();
    httpMock.expectOne('/api/auth/login').flush({
      accessToken: 't', tokenType: 'Bearer', expiresIn: 900, expiresAt: '',
      user: { id: 7, email: 'kavya@example.com', fullName: 'Kavya Rao', emailVerified: false, roles: ['ROLE_CUSTOMER'], createdAt: '' },
    });
  }

  it('sends anonymous users to sign in', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const wishlist = TestBed.inject(WishlistService);
    TestBed.tick();
    wishlist.toggle(5);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.objectContaining({
      queryParams: expect.objectContaining({ reason: 'wishlist' }),
    }));
    httpMock.expectNone('/api/account/wishlist/5');
  });

  it('loads saved ids after sign-in and toggles optimistically', () => {
    const wishlist = TestBed.inject(WishlistService);
    signIn();
    TestBed.tick();
    httpMock.expectOne('/api/account/wishlist/ids').flush([3]);
    expect(wishlist.has(3)).toBe(true);

    wishlist.toggle(5);
    expect(wishlist.has(5)).toBe(true);
    const put = httpMock.expectOne('/api/account/wishlist/5');
    expect(put.request.method).toBe('PUT');
    put.flush(null, { status: 204, statusText: 'No Content' });

    wishlist.toggle(3);
    expect(wishlist.has(3)).toBe(false);
    expect(httpMock.expectOne('/api/account/wishlist/3').request.method).toBe('DELETE');
  });

  it('rolls back when the server refuses', () => {
    const wishlist = TestBed.inject(WishlistService);
    signIn();
    TestBed.tick();
    httpMock.expectOne('/api/account/wishlist/ids').flush([]);

    wishlist.toggle(9);
    expect(wishlist.has(9)).toBe(true);
    httpMock.expectOne('/api/account/wishlist/9').flush({ code: 'RESOURCE_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    expect(wishlist.has(9)).toBe(false);
  });
});
