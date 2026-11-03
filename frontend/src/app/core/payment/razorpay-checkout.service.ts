import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { PaymentApi } from '../api/payment.api';
import { SITE } from '../config/site.config';
import { OrderDetail } from '../models/order.models';

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js';

/** The parts of Razorpay Checkout.js this app uses. */
interface RazorpaySuccess {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

interface RazorpayFailure {
  error: { code?: string; description?: string; metadata?: { order_id?: string; payment_id?: string } };
}

interface RazorpayInstance {
  open(): void;
  on(event: 'payment.failed', handler: (response: RazorpayFailure) => void): void;
}

type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayInstance;

declare global {
  interface Window {
    Razorpay?: RazorpayConstructor;
  }
}

export type PaymentOutcome =
  | { outcome: 'paid'; order: OrderDetail }
  | { outcome: 'dismissed' }
  | { outcome: 'error'; error: unknown };

/**
 * Opens Razorpay Standard Checkout for an order. The server creates the Razorpay order and sets
 * the amount; the result is only trusted after the server has checked the payment signature.
 */
@Injectable({ providedIn: 'root' })
export class RazorpayCheckoutService {
  private readonly api = inject(PaymentApi);
  private readonly document = inject(DOCUMENT);
  private script?: Promise<RazorpayConstructor>;

  /**
   * Starts (or retries) payment and resolves when the customer has paid, closed the window, or
   * something went wrong. Throws only when the payment could not be started at all.
   */
  async pay(orderNumber: string): Promise<PaymentOutcome> {
    const start = await firstValueFrom(this.api.start(orderNumber));
    const Razorpay = await this.load();
    return new Promise<PaymentOutcome>((resolve) => {
      let settled = false;
      const finish = (result: PaymentOutcome) => {
        if (!settled) {
          settled = true;
          resolve(result);
        }
      };
      const checkout = new Razorpay({
        key: start.keyId,
        amount: start.amount,
        currency: start.currency,
        order_id: start.razorpayOrderId,
        name: SITE.brandName,
        description: start.description,
        prefill: start.prefill,
        notes: { order_number: start.orderNumber },
        theme: { color: '#111111' },
        retry: { enabled: true },
        modal: { confirm_close: true, ondismiss: () => finish({ outcome: 'dismissed' }) },
        handler: (response: RazorpaySuccess) => {
          firstValueFrom(this.api.verify({
            orderNumber,
            razorpayOrderId: response.razorpay_order_id,
            razorpayPaymentId: response.razorpay_payment_id,
            razorpaySignature: response.razorpay_signature,
          })).then(
            (order) => finish({ outcome: 'paid', order }),
            (error: unknown) => finish({ outcome: 'error', error }),
          );
        },
      });
      // Razorpay keeps its window open so the customer can try another method; just record the failure.
      checkout.on('payment.failed', (response) => {
        void firstValueFrom(this.api.failed({
          orderNumber,
          razorpayOrderId: response.error.metadata?.order_id ?? start.razorpayOrderId,
          razorpayPaymentId: response.error.metadata?.payment_id,
          code: response.error.code,
          description: response.error.description,
        })).catch(() => undefined);
      });
      checkout.open();
    });
  }

  /** Loads checkout.js once. */
  private load(): Promise<RazorpayConstructor> {
    const win = this.document.defaultView as Window | null;
    if (win?.Razorpay) return Promise.resolve(win.Razorpay);
    this.script ??= new Promise<RazorpayConstructor>((resolve, reject) => {
      const el = this.document.createElement('script');
      el.src = SCRIPT_URL;
      el.async = true;
      el.onload = () => (win?.Razorpay ? resolve(win.Razorpay) : reject(new Error('Razorpay did not load.')));
      el.onerror = () => {
        this.script = undefined;
        el.remove();
        reject(new Error('The payment window could not be loaded. Check your internet connection and try again.'));
      };
      this.document.head.appendChild(el);
    });
    return this.script;
  }
}
