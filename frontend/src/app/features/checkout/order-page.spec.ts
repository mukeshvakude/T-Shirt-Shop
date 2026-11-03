import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { OrderDetail } from '../../core/models/order.models';
import { OrderPage } from './order-page';

function order(patch: Partial<OrderDetail> = {}): OrderDetail {
  return {
    orderNumber: 'TS-20261103-123456', status: 'SHIPPED', paymentStatus: 'PENDING', paymentMethod: 'COD',
    placedAt: '2026-11-03T10:00:00Z', reservationExpiresAt: null, awaitingPayment: false, items: [], itemsTotal: 998,
    customizationTotal: 0, shipping: 49, discount: 0, couponCode: null, total: 1047, currency: 'INR',
    contact: { name: 'Isha', email: 'i@example.com', phone: '9876543210' },
    shippingAddress: { fullName: 'Isha', phone: '9876543210', line1: '7 Hill Road', line2: null, landmark: null, city: 'Mumbai', state: 'Maharashtra', pincode: '400050' },
    note: null, cancelReason: null,
    history: [
      { status: 'CONFIRMED', at: '2026-11-03T10:00:00Z', note: null },
      { status: 'PROCESSING', at: '2026-11-03T11:00:00Z', note: null },
      { status: 'SHIPPED', at: '2026-11-03T12:00:00Z', note: null },
    ],
    confirmedAt: '2026-11-03T10:00:00Z', shippedAt: '2026-11-03T12:00:00Z', deliveredAt: null, cancelledAt: null,
    courierName: 'Delhivery', trackingNumber: 'DLV123', canCancel: false, invoiceAvailable: true,
    ...patch,
  };
}

describe('OrderPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [OrderPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  async function render(o: OrderDetail) {
    const fixture = TestBed.createComponent(OrderPage);
    fixture.componentRef.setInput('number', o.orderNumber);
    fixture.detectChanges();
    http.expectOne(`/api/orders/${o.orderNumber}`).flush(o);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('shows delivery progress and tracking for a shipped order', async () => {
    const el: HTMLElement = (await render(order())).nativeElement;
    const done = [...el.querySelectorAll('.steps li.done strong')].map((s) => s.textContent?.trim());
    expect(done).toEqual(['Confirmed', 'Being prepared', 'Shipped']);
    expect(el.querySelector('.steps li.current strong')?.textContent).toContain('Shipped');
    expect(el.querySelector('.courier')?.textContent).toContain('DLV123');
    expect(el.textContent).toContain('Download invoice');
    expect(el.textContent).not.toContain('Cancel order');
  });

  it('cancels with the chosen reason and shows the result', async () => {
    const fixture = await render(order({ status: 'CONFIRMED', shippedAt: null, courierName: null, trackingNumber: null, canCancel: true,
      history: [{ status: 'CONFIRMED', at: '2026-11-03T10:00:00Z', note: null }] }));
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of({ reason: 'Ordered by mistake' }) } as never);

    const done = fixture.componentInstance.cancel();
    await Promise.resolve();
    await Promise.resolve();
    const req = http.expectOne('/api/orders/TS-20261103-123456/cancel');
    expect(req.request.body).toEqual({ reason: 'Ordered by mistake' });
    req.flush(order({ status: 'CANCELLED', paymentStatus: 'CANCELLED', canCancel: false, invoiceAvailable: false,
      cancelReason: 'Cancelled by you: Ordered by mistake' }));
    await done;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.hero h1')?.textContent).toContain('Order cancelled');
    expect(el.querySelector('.steps')).toBeNull();
  });

  it('keeps the order when the dialog is closed', async () => {
    const fixture = await render(order({ status: 'CONFIRMED', canCancel: true }));
    vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({ afterClosed: () => of(undefined) } as never);
    await fixture.componentInstance.cancel();
    http.expectNone('/api/orders/TS-20261103-123456/cancel');
  });
});
