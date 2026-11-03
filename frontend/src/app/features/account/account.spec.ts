import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';

import { AuthService } from '../../core/auth/auth.service';
import { Address, Dashboard } from '../../core/models/account.models';
import { AddressDialog } from './addresses/address-dialog';
import { AccountDashboard } from './dashboard/account-dashboard';
import { AccountProfile } from './profile/account-profile';

type WithStateControl = { form: { controls: { state: { setValue(value: string): void } } } };

const USER = { id: 7, email: 'kavya@example.com', fullName: 'Kavya Rao', phone: '9876543210', emailVerified: false,
  roles: ['ROLE_CUSTOMER' as const], createdAt: '2026-11-01T00:00:00Z' };

describe('Customer account', () => {
  let httpMock: HttpTestingController;

  function setup(extra: unknown[] = []) {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), ...(extra as never[])],
    });
    httpMock = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthService);
    auth.login('kavya@example.com', 'Secret123').subscribe();
    httpMock.expectOne('/api/auth/login').flush({ accessToken: 't1', tokenType: 'Bearer', expiresIn: 900, expiresAt: '', user: USER });
    return auth;
  }

  const type = (el: HTMLElement, name: string, value: string) => {
    const input = el.querySelector<HTMLInputElement>(`[formcontrolname=${name}]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  afterEach(() => httpMock.verify());

  describe('dashboard', () => {
    function render(data: Dashboard) {
      setup();
      const fixture = TestBed.createComponent(AccountDashboard);
      fixture.detectChanges();
      httpMock.expectOne('/api/account/dashboard').flush(data);
      fixture.detectChanges();
      return fixture.nativeElement as HTMLElement;
    }

    const base: Dashboard = {
      profile: USER, orders: { total: 0, active: 0, delivered: 0, cancelled: 0 }, recentOrders: [],
      wishlistCount: 2, addressCount: 0,
    };

    it('greets the customer and explains empty states', () => {
      const el = render(base);
      expect(el.querySelector('h1')?.textContent).toContain('Kavya');
      expect(el.textContent).toContain('You have not placed an order yet');
      expect(el.textContent).toContain('No saved address yet');
      expect([...el.querySelectorAll('.stat .num')].map((n) => n.textContent)).toEqual(['0', '0', '0', '2']);
    });

    it('lists recent orders with friendly statuses and the default address', () => {
      const el = render({
        ...base,
        orders: { total: 2, active: 1, delivered: 1, cancelled: 0 },
        recentOrders: [
          { orderNumber: 'TS-2', placedAt: '2026-10-20T10:00:00Z', orderStatus: 'OUT_FOR_DELIVERY', paymentStatus: 'SUCCESS', grandTotal: 478, itemCount: 2 },
          { orderNumber: 'TS-1', placedAt: '2026-10-01T10:00:00Z', orderStatus: 'DELIVERED', paymentStatus: 'SUCCESS', grandTotal: 999, itemCount: 1 },
        ],
        addressCount: 1,
        defaultAddress: { id: 1, label: 'Home', fullName: 'Kavya Rao', phone: '9876543210', line1: '12 MG Road', city: 'Pune',
          state: 'Maharashtra', pincode: '411001', country: 'India', isDefault: true },
      });
      expect([...el.querySelectorAll('tbody .pill')].map((p) => p.textContent)).toEqual(['Out for delivery', 'Delivered']);
      expect(el.querySelector('address')?.textContent).toContain('Pune, Maharashtra 411001');
    });
  });

  describe('address dialog', () => {
    function render(data: Address | null) {
      const close = vi.fn();
      setup([{ provide: MAT_DIALOG_DATA, useValue: data }, { provide: MatDialogRef, useValue: { close } }]);
      const fixture = TestBed.createComponent(AddressDialog);
      fixture.detectChanges();
      httpMock.expectOne('/api/account/states').flush(['Goa', 'Maharashtra']);
      fixture.detectChanges();
      return { fixture, el: fixture.nativeElement as HTMLElement, close };
    }

    it('prefills the customer and validates pincode and phone before saving', () => {
      const { fixture, el, close } = render(null);
      expect(el.querySelector<HTMLInputElement>('[formcontrolname=fullName]')!.value).toBe('Kavya Rao');
      type(el, 'line1', '12 MG Road');
      type(el, 'city', 'Pune');
      type(el, 'pincode', '012345');
      // mat-select is driven through the form control (the overlay is not part of this test).
      (fixture.componentInstance as unknown as WithStateControl).form.controls.state.setValue('Maharashtra');
      el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      httpMock.expectNone('/api/account/addresses');
      expect(el.textContent).toContain('Enter a 6-digit pincode');

      type(el, 'pincode', '411001');
      el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      const req = httpMock.expectOne('/api/account/addresses');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toMatchObject({ label: 'Home', pincode: '411001', state: 'Maharashtra', phone: '9876543210', line2: null });
      req.flush({ id: 5 });
      expect(close).toHaveBeenCalledWith({ id: 5 });
    });

    it('keeps the default checkbox locked for the default address', () => {
      const { el } = render({ id: 3, label: 'Home', fullName: 'Kavya', phone: '9876543210', line1: 'x', city: 'Pune',
        state: 'Goa', pincode: '403001', country: 'India', isDefault: true });
      expect(el.querySelector<HTMLInputElement>('mat-checkbox input')!.disabled).toBe(true);
      expect(el.textContent).toContain('This is your default address');
    });
  });

  describe('profile', () => {
    it('applies the new session after a password change', () => {
      const auth = setup();
      const fixture = TestBed.createComponent(AccountProfile);
      fixture.detectChanges();
      const el = fixture.nativeElement as HTMLElement;
      const forms = el.querySelectorAll('form');
      const passwordForm = forms[2];
      const typeIn = (form: Element, name: string, value: string) => {
        const input = form.querySelector<HTMLInputElement>(`[formcontrolname=${name}]`)!;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      };
      typeIn(passwordForm, 'currentPassword', 'Secret123');
      typeIn(passwordForm, 'password', 'NewSecret456');
      typeIn(passwordForm, 'confirmPassword', 'NewSecret456');
      passwordForm.dispatchEvent(new Event('submit'));

      const req = httpMock.expectOne('/api/account/password');
      expect(req.request.body).toEqual({ currentPassword: 'Secret123', newPassword: 'NewSecret456' });
      expect(req.request.withCredentials).toBe(true);
      req.flush({ accessToken: 't2', tokenType: 'Bearer', expiresIn: 900, expiresAt: '', user: USER });
      expect(auth.token()).toBe('t2');
    });

    it('shows a wrong current password next to the field', () => {
      setup();
      const fixture = TestBed.createComponent(AccountProfile);
      fixture.detectChanges();
      const emailForm = (fixture.nativeElement as HTMLElement).querySelectorAll('form')[1];
      const typeIn = (name: string, value: string) => {
        const input = emailForm.querySelector<HTMLInputElement>(`[formcontrolname=${name}]`)!;
        input.value = value;
        input.dispatchEvent(new Event('input'));
      };
      typeIn('newEmail', 'new@example.com');
      typeIn('currentPassword', 'Wrong1234');
      emailForm.dispatchEvent(new Event('submit'));
      httpMock.expectOne('/api/account/email').flush(
        { code: 'INVALID_CURRENT_PASSWORD', detail: 'Your current password is not correct.' },
        { status: 400, statusText: 'Bad Request' },
      );
      emailForm.querySelector<HTMLInputElement>('[formcontrolname=currentPassword]')!.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
      expect(emailForm.textContent).toContain('That password is not correct');
    });
  });
});

describe('landmarkText', () => {
  it('adds "Near" only when the customer did not write a direction', async () => {
    const { landmarkText } = await import('../../core/models/account.models');
    expect(landmarkText('Shivaji Park')).toBe('Near Shivaji Park');
    expect(landmarkText('near Shivaji Park')).toBe('near Shivaji Park');
    expect(landmarkText('Opposite City Mall')).toBe('Opposite City Mall');
    expect(landmarkText('Opp. Bus Stand')).toBe('Opp. Bus Stand');
    expect(landmarkText('  ')).toBe('');
    expect(landmarkText(undefined)).toBe('');
  });
});
