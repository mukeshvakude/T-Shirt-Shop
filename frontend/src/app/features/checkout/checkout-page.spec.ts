import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { CheckoutSummary } from '../../core/models/order.models';
import { CheckoutPage } from './checkout-page';

function summary(total = 947.2): CheckoutSummary {
  return {
    cart: {
      currency: 'INR', itemCount: 2, subtotal: 998, coupon: null, discount: 0, shipping: 49, total, freeShippingThreshold: 999,
      amountToFreeShipping: 1, taxInclusive: true, canCheckout: true, notices: [],
      items: [{ key: 'item-1', id: 1, type: 'PRODUCT', productId: 1, productSlug: 'tee', name: 'Tee', imageUrl: null, sku: 'S1',
        sizeCode: 'M', sizeLabel: 'Medium', colorCode: 'BLACK', colorName: 'Black', colorHex: null, quantity: 2, maxQuantity: 5,
        unitPrice: 499, unitMrp: 599, lineTotal: 998, status: 'OK', message: null, variants: [], design: null }],
    },
    totals: { itemsTotal: 998, customizationTotal: 0, shipping: 49, discount: 0, total },
    contact: { name: 'Meera Iyer', email: 'meera@example.com', phone: '9876543210' },
    addresses: [],
    paymentOptions: [
      { method: 'RAZORPAY', available: false, message: 'Online payment is not available yet. Please choose Cash on Delivery.' },
      { method: 'COD', available: true, message: null },
    ],
    reservationMinutes: 15,
  };
}

describe('CheckoutPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CheckoutPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render(s: CheckoutSummary) {
    const fixture = TestBed.createComponent(CheckoutPage);
    fixture.detectChanges();
    http.expectOne('/api/account/states').flush(['Maharashtra', 'Karnataka']);
    http.expectOne('/api/checkout').flush(s);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  function fillAddress(el: HTMLElement) {
    const set = (name: string, value: string) => {
      const input = el.querySelector<HTMLInputElement | HTMLSelectElement>(`[formcontrolname=${name}]`)!;
      input.value = value;
      input.dispatchEvent(new Event(input.tagName === 'SELECT' ? 'change' : 'input'));
    };
    set('line1', '12 MG Road');
    set('city', 'Pune');
    set('state', 'Maharashtra');
    set('pincode', '411001');
  }

  it('prefills contact details and picks the only available payment method', async () => {
    const el: HTMLElement = (await render(summary())).nativeElement;
    expect(el.querySelector<HTMLInputElement>('[formcontrolname=email]')!.value).toBe('meera@example.com');
    const radios = el.querySelectorAll<HTMLInputElement>('input[type=radio]');
    expect(radios[0].disabled).toBe(true);
    expect(radios[1].checked).toBe(true);
    expect(el.textContent).toContain('Online payment is not available yet');
  });

  it('uses the contact name and phone for the receiver until the customer edits them', async () => {
    const s = summary();
    s.contact.phone = null;
    const fixture = await render(s);
    const el: HTMLElement = fixture.nativeElement;
    const contactPhone = el.querySelectorAll<HTMLInputElement>('[formcontrolname=phone]')[0];
    contactPhone.value = '9123456780';
    contactPhone.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const receiverPhone = el.querySelectorAll<HTMLInputElement>('[formcontrolname=phone]')[1];
    expect(receiverPhone.value).toBe('9123456780');

    receiverPhone.value = '9000000001';
    receiverPhone.dispatchEvent(new Event('input'));
    contactPhone.value = '9111111111';
    contactPhone.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(receiverPhone.value).toBe('9000000001');
  });

  it('sends choices and the displayed total, never prices, then opens the order', async () => {
    const fixture = await render(summary());
    const el: HTMLElement = fixture.nativeElement;
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fillAddress(el);
    fixture.detectChanges();

    const done = fixture.componentInstance.placeOrder();
    const req = http.expectOne('/api/checkout/place-order');
    expect(req.request.body).toEqual(expect.objectContaining({
      addressId: null,
      saveAddress: true,
      paymentMethod: 'COD',
      expectedTotal: 947.2,
      address: expect.objectContaining({ city: 'Pune', state: 'Maharashtra', pincode: '411001', makeDefault: false }),
    }));
    req.flush({ orderNumber: 'TS-20261103-123456' });
    await done;
    expect(navigate).toHaveBeenCalledWith(['/order', 'TS-20261103-123456'], expect.anything());
  });

  it('reloads the summary when the server reports a price change', async () => {
    const fixture = await render(summary());
    fillAddress(fixture.nativeElement);
    const done = fixture.componentInstance.placeOrder();
    http.expectOne('/api/checkout/place-order').flush(
      { code: 'PRICE_CHANGED', detail: 'Your order total is now ₹960. Please check the summary and place the order again.' },
      { status: 409, statusText: 'Conflict' },
    );
    await Promise.resolve();
    http.expectOne('/api/checkout').flush(summary(960));
    await done;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('Your order total is now ₹960');
    expect(el.querySelector('.summary .total')!.textContent).toContain('₹960');
  });
});
