import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Register } from './register';

describe('Register page', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function render(returnUrl?: string) {
    const fixture = TestBed.createComponent(Register);
    if (returnUrl) fixture.componentRef.setInput('returnUrl', returnUrl);
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    const type = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement>(`input[formcontrolname=${name}]`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    const fill = (password = 'Secret123', confirm = 'Secret123') => {
      type('fullName', 'Kavya Rao');
      type('email', 'kavya@example.com');
      type('password', password);
      type('confirmPassword', confirm);
      el.querySelector<HTMLInputElement>('mat-checkbox input')!.click();
    };
    const submit = () => {
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      fixture.detectChanges();
    };
    return { fixture, el, fill, submit, type };
  }

  it('requires matching passwords, a strong password and accepted terms', () => {
    const { el, fill, submit, type } = render();
    submit();
    httpMock.expectNone('/api/auth/register');
    expect(el.textContent).toContain('Please accept the terms');

    fill('Secret123', 'Secret124');
    submit();
    httpMock.expectNone('/api/auth/register');
    expect(el.textContent).toContain('The passwords do not match.');

    type('password', 'password');
    type('confirmPassword', 'password');
    submit();
    httpMock.expectNone('/api/auth/register');
  });

  it('creates the account and returns to the previous page', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { fill, submit } = render('/about');
    fill();
    submit();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({ fullName: 'Kavya Rao', email: 'kavya@example.com', phone: null, password: 'Secret123' });
    req.flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900, expiresAt: '',
      user: { id: 9, email: 'kavya@example.com', fullName: 'Kavya Rao', emailVerified: false, roles: ['ROLE_CUSTOMER'], createdAt: '' } });
    expect(navigate).toHaveBeenCalledWith('/about');
  });

  it('flags an email that is already registered and ignores unsafe return URLs', () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const { el, fixture, fill, submit } = render('//evil.example');
    fill();
    submit();
    httpMock.expectOne('/api/auth/register').flush(
      { code: 'EMAIL_ALREADY_REGISTERED', detail: 'An account with this email already exists.' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();
    expect(el.textContent).toContain('An account with this email already exists');

    el.querySelector<HTMLInputElement>('input[formcontrolname=email]')!.value = 'kavya2@example.com';
    el.querySelector<HTMLInputElement>('input[formcontrolname=email]')!.dispatchEvent(new Event('input'));
    submit();
    httpMock.expectOne('/api/auth/register').flush({ accessToken: 't', tokenType: 'Bearer', expiresIn: 900, expiresAt: '',
      user: { id: 9, email: 'kavya2@example.com', fullName: 'Kavya Rao', emailVerified: false, roles: ['ROLE_CUSTOMER'], createdAt: '' } });
    expect(navigate).toHaveBeenCalledWith('/');
  });
});
