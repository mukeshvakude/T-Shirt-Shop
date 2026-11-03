import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { CheckoutApi } from '../../core/api/checkout.api';
import { NotifyService } from '../../core/notify.service';
import { RazorpayCheckoutService } from '../../core/payment/razorpay-checkout.service';
import { ORDER_STATUS_LABELS, landmarkText } from '../../core/models/account.models';
import { OrderDetail, PAYMENT_STATUS_LABELS } from '../../core/models/order.models';
import { ImgFallback } from '../../shared/img-fallback.directive';
import { CancelOrderDialog, CancelOrderData } from './cancel-order-dialog';
import { InrPipe } from '../../shared/inr.pipe';

/** Order confirmation and details: thank-you message, items, address, totals and status history. */
@Component({
  selector: 'app-order-page',
  imports: [RouterLink, DatePipe, MatProgressSpinnerModule, ImgFallback, InrPipe],
  templateUrl: './order-page.html',
  styleUrl: './order-page.scss',
})
export class OrderPage implements OnInit, OnDestroy {
  private readonly api = inject(CheckoutApi);
  private readonly razorpay = inject(RazorpayCheckoutService);
  private readonly notify = inject(NotifyService);
  protected readonly paying = signal(false);
  protected readonly working = signal(false);
  private readonly dialog = inject(MatDialog);

  /** Route parameter and query flag (component input binding). */
  readonly number = input.required<string>();
  readonly placed = input<string>();

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly now = signal(Date.now());
  protected readonly statusLabels = ORDER_STATUS_LABELS;
  protected readonly paymentLabels = PAYMENT_STATUS_LABELS;
  protected readonly landmarkText = landmarkText;

  /** Minutes left in the payment window for an unpaid online order. */
  protected readonly minutesLeft = computed(() => {
    const o = this.order();
    if (!o?.awaitingPayment || !o.reservationExpiresAt) return null;
    return Math.max(0, Math.ceil((new Date(o.reservationExpiresAt).getTime() - this.now()) / 60000));
  });

  private timer?: ReturnType<typeof setInterval>;

  async ngOnInit(): Promise<void> {
    try {
      this.order.set(await firstValueFrom(this.api.order(this.number())));
    } catch (error) {
      this.error.set(apiErrorMessage(error, 'This order could not be found.'));
    }
    this.timer = setInterval(() => this.now.set(Date.now()), 30_000);
  }

  /** Delivery progress: each step with its time once reached. */
  protected readonly steps = computed(() => {
    const o = this.order();
    if (!o) return [];
    const at = (status: string) => o.history.find((h) => h.status === status)?.at ?? null;
    const steps = [
      { key: 'CONFIRMED', label: 'Confirmed', icon: 'task_alt', at: o.confirmedAt ?? at('CONFIRMED') },
      { key: 'PROCESSING', label: 'Being prepared', icon: 'inventory_2', at: at('PROCESSING') },
      { key: 'SHIPPED', label: 'Shipped', icon: 'local_shipping', at: o.shippedAt ?? at('SHIPPED') },
      { key: 'OUT_FOR_DELIVERY', label: 'Out for delivery', icon: 'two_wheeler', at: at('OUT_FOR_DELIVERY') },
      { key: 'DELIVERED', label: 'Delivered', icon: 'home', at: o.deliveredAt ?? at('DELIVERED') },
    ];
    return steps.map((s) => ({ ...s, current: s.key === o.status }));
  });

  async cancel(): Promise<void> {
    const o = this.order();
    if (!o || this.working()) return;
    const data: CancelOrderData = { orderNumber: o.orderNumber, paidOnline: o.paymentStatus === 'SUCCESS' && o.paymentMethod === 'RAZORPAY' };
    const result = await firstValueFrom(this.dialog.open(CancelOrderDialog, { data, width: '460px', maxWidth: '94vw' }).afterClosed());
    if (!result) return;
    this.working.set(true);
    try {
      const updated = await firstValueFrom(this.api.cancel(o.orderNumber, result.reason));
      this.order.set(updated);
      this.notify.success(updated.paymentStatus === 'REFUNDED' ? 'Order cancelled. Your refund has been started.' : 'Order cancelled.');
    } catch (error) {
      this.notify.error(error, 'The order could not be cancelled.');
      this.order.set(await firstValueFrom(this.api.order(o.orderNumber)).catch(() => o));
    } finally {
      this.working.set(false);
    }
  }

  async downloadInvoice(): Promise<void> {
    const o = this.order();
    if (!o || this.working()) return;
    this.working.set(true);
    try {
      const blob = await firstValueFrom(this.api.invoice(o.orderNumber));
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'INV-' + o.orderNumber.slice(3) + '.pdf';
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.notify.error(error, 'The invoice could not be downloaded.');
    } finally {
      this.working.set(false);
    }
  }

  protected copyTracking(value: string | null): void {
    if (!value) return;
    void navigator.clipboard?.writeText(value).then(() => this.notify.success('Tracking number copied.'));
  }

  /** Pay (or try again) for an unpaid online order. */
  async pay(): Promise<void> {
    const o = this.order();
    if (!o || this.paying()) return;
    this.paying.set(true);
    try {
      const result = await this.razorpay.pay(o.orderNumber);
      if (result.outcome === 'paid') {
        this.order.set(result.order);
        this.notify.success(result.order.status === 'CONFIRMED' ? 'Payment received. Thank you!' : 'Payment received.');
      } else {
        if (result.outcome === 'error') this.notify.error(result.error, 'The payment could not be completed. Please try again.');
        else this.notify.success('Payment not completed. You can try again any time before the order expires.');
        this.order.set(await firstValueFrom(this.api.order(o.orderNumber)));
      }
    } catch (error) {
      this.notify.error(error, 'The payment window could not be opened. Please try again.');
    } finally {
      this.paying.set(false);
    }
  }

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }
}
