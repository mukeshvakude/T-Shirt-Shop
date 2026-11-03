import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Login } from './login';

describe('Login page', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function render() {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (selector: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    return { fixture, el, type };
  }

  it('does not submit an invalid form', async () => {
    const { fixture, el } = render();
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    httpMock.expectNone('/api/auth/login');
    expect(el.textContent).toContain('Email is required');
  });

  it('logs an admin in and opens the portal', async () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, type } = render();
    type('input[type=email]', 'admin@test.local');
    type('input[formcontrolname=password]', 'TestAdmin123');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.body).toEqual({ email: 'admin@test.local', password: 'TestAdmin123' });
    req.flush({
      accessToken: 't', tokenType: 'Bearer', expiresIn: 900, expiresAt: '',
      user: { id: 1, email: 'admin@test.local', fullName: 'Admin', emailVerified: true, roles: ['ROLE_ADMIN'], createdAt: '' },
    });
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith('/admin');
  });

  it('shows the server message on failure and sends customers back to the shop', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fixture, el, type } = render();
    type('input[type=email]', 'asha@example.com');
    type('input[formcontrolname=password]', 'Wrong1234');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    httpMock.expectOne('/api/auth/login').flush(
      { code: 'INVALID_CREDENTIALS', detail: 'Invalid email or password.' },
      { status: 401, statusText: 'Unauthorized' },
    );
    fixture.detectChanges();
    expect(el.querySelector('.error')?.textContent).toContain('Invalid email or password.');

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    httpMock.expectOne('/api/auth/login').flush({
      accessToken: 't', tokenType: 'Bearer', expiresIn: 900, expiresAt: '',
      user: { id: 2, email: 'asha@example.com', fullName: 'Asha', emailVerified: false, roles: ['ROLE_CUSTOMER'], createdAt: '' },
    });
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith('/');
  });
});
