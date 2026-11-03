import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PaymentStart } from '../models/order.models';
import { RazorpayCheckoutService } from './razorpay-checkout.service';

const START: PaymentStart = {
  keyId: 'rzp_test_abc', razorpayOrderId: 'order_1', amount: 104700, currency: 'INR', orderNumber: 'TS-20261103-000001',
  description: 'Order TS-20261103-000001', prefill: { name: 'Ravi', email: 'r@example.com', contact: '9876543210' }, holdUntil: null,
};

/** Stand-in for Razorpay Checkout.js that records options and lets the test play the customer. */
class FakeRazorpay {
  static last: FakeRazorpay;
  failed?: (r: unknown) => void;
  opened = false;
  constructor(readonly options: Record<string, any>) {
    FakeRazorpay.last = this;
  }
  on(_: string, handler: (r: unknown) => void) {
    this.failed = handler;
  }
  open() {
    this.opened = true;
  }
}

describe('RazorpayCheckoutService', () => {
  let http: HttpTestingController;
  let service: RazorpayCheckoutService;

  beforeEach(() => {
    (window as any).Razorpay = FakeRazorpay;
    FakeRazorpay.last = undefined as unknown as FakeRazorpay;
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(RazorpayCheckoutService);
  });

  afterEach(() => delete (window as any).Razorpay);

  async function open() {
    const result = service.pay('TS-20261103-000001');
    const req = http.expectOne('/api/payments/create-order');
    expect(req.request.body).toEqual({ orderNumber: 'TS-20261103-000001' }); // no amount from the browser
    req.flush(START);
    await vi.waitFor(() => expect(FakeRazorpay.last?.opened).toBe(true));
    return result;
  }

  it('opens checkout with the server order and verifies the payment on the server', async () => {
    const result = open();
    await vi.waitFor(() => expect(FakeRazorpay.last.options['order_id']).toBe('order_1'));
    const options = FakeRazorpay.last.options;
    expect(options['key']).toBe('rzp_test_abc');
    expect(options['amount']).toBe(104700);
    expect(JSON.stringify(options)).not.toContain('secret');

    options['handler']({ razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_1', razorpay_signature: 'abc123' });
    const verify = http.expectOne('/api/payments/verify');
    expect(verify.request.body).toEqual({
      orderNumber: 'TS-20261103-000001', razorpayOrderId: 'order_1', razorpayPaymentId: 'pay_1', razorpaySignature: 'abc123',
    });
    verify.flush({ orderNumber: 'TS-20261103-000001', status: 'CONFIRMED' });
    const outcome = await result;
    expect(outcome.outcome).toBe('paid');
  });

  it('reports failed attempts and resolves when the customer closes the window', async () => {
    const result = open();
    await vi.waitFor(() => expect(FakeRazorpay.last.failed).toBeDefined());
    FakeRazorpay.last.failed!({ error: { code: 'BAD_REQUEST_ERROR', description: 'Declined', metadata: { order_id: 'order_1', payment_id: 'pay_2' } } });
    const failed = http.expectOne('/api/payments/failed');
    expect(failed.request.body).toEqual(expect.objectContaining({ razorpayPaymentId: 'pay_2', code: 'BAD_REQUEST_ERROR' }));
    failed.flush({});

    FakeRazorpay.last.options['modal'].ondismiss();
    expect((await result).outcome).toBe('dismissed');
  });

  it('a rejected signature is reported as an error, not as paid', async () => {
    const result = open();
    await vi.waitFor(() => expect(FakeRazorpay.last.options).toBeDefined());
    FakeRazorpay.last.options['handler']({ razorpay_payment_id: 'pay_1', razorpay_order_id: 'order_1', razorpay_signature: 'forged' });
    http.expectOne('/api/payments/verify').flush({ code: 'INVALID_SIGNATURE' }, { status: 400, statusText: 'Bad Request' });
    expect((await result).outcome).toBe('error');
  });
});
