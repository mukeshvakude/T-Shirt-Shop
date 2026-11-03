import { DatePipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { apiErrorMessage } from '../../core/api/api-error';
import { CheckoutApi } from '../../core/api/checkout.api';
import { ORDER_STATUS_LABELS, landmarkText } from '../../core/models/account.models';
import { OrderDetail, PAYMENT_STATUS_LABELS } from '../../core/models/order.models';
import { ImgFallback } from '../../shared/img-fallback.directive';
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

  ngOnDestroy(): void {
    clearInterval(this.timer);
  }
}
