import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CheckoutApi } from '../../../core/api/checkout.api';
import { ORDER_STATUS_LABELS } from '../../../core/models/account.models';
import { OrderSummary, PAYMENT_STATUS_LABELS } from '../../../core/models/order.models';
import { NotifyService } from '../../../core/notify.service';
import { ImgFallback } from '../../../shared/img-fallback.directive';
import { InrPipe } from '../../../shared/inr.pipe';

/** The customer's order history, newest first, ten at a time. */
@Component({
  selector: 'app-account-orders',
  imports: [RouterLink, DatePipe, InrPipe, ImgFallback],
  styleUrl: '../account-pages.scss',
  template: `
    <header class="page-head">
      <h1>Orders</h1>
      <p>Track, cancel or download the invoice of your orders.</p>
    </header>
    @if (loading() && !orders().length) {
      <section class="panel"><p class="muted">Loading…</p></section>
    } @else if (orders().length === 0) {
      <section class="panel">
        <div class="empty">
          <span class="material-symbols-outlined">package_2</span>
          <p>No orders yet. When you place an order, you will see its status, payment and delivery updates here.</p>
          <a class="btn" routerLink="/shop">Start shopping</a>
        </div>
      </section>
    } @else {
      <div class="list">
        @for (o of orders(); track o.orderNumber) {
          <a class="order" [routerLink]="['/order', o.orderNumber]">
            <div class="thumbs">
              @for (img of o.images; track img) { <img appImgFallback [src]="img" alt="" loading="lazy" /> }
            </div>
            <div class="what">
              <strong>{{ o.orderNumber }}</strong>
              <small>{{ o.placedAt | date: 'd MMM y' }} · {{ o.itemCount }} item{{ o.itemCount === 1 ? '' : 's' }}
                @if (o.firstItemName) { · {{ o.firstItemName }} }</small>
              <small>{{ o.paymentMethod === 'COD' ? 'Cash on Delivery' : 'Online' }} · {{ paymentLabels[o.paymentStatus] ?? o.paymentStatus }}</small>
            </div>
            <div class="end">
              <span class="pill" [attr.data-status]="o.status">{{ o.awaitingPayment ? 'Awaiting payment' : (labels[o.status] ?? o.status) }}</span>
              <strong>{{ o.total | inr }}</strong>
            </div>
          </a>
        }
      </div>
      @if (!last()) {
        <button type="button" class="btn outline more" (click)="load(page() + 1)" [disabled]="loading()">
          {{ loading() ? 'Loading…' : 'Show older orders' }}
        </button>
      }
    }
  `,
  styles: `
    .list { display: grid; gap: 12px; }
    .order { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; gap: 16px; align-items: center; padding: 14px 16px;
      border: 1px solid var(--shop-line); border-radius: 16px; background: #fff; color: inherit; text-decoration: none;
      &:hover { border-color: #111; } }
    .thumbs { display: flex; }
    .thumbs img { width: 52px; height: 64px; object-fit: cover; border-radius: 8px; background: #f3f1ec; border: 2px solid #fff; }
    .thumbs img + img { margin-left: -18px; }
    .what { display: grid; gap: 2px; min-width: 0; small { color: var(--shop-muted); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } }
    .end { display: grid; gap: 6px; justify-items: end; strong { font-size: 16px; } }
    .more { margin: 16px auto 0; display: flex; }
    @media (max-width: 560px) {
      .order { grid-template-columns: minmax(0, 1fr) auto; }
      .thumbs { display: none; }
    }
  `,
})
export class AccountOrders implements OnInit {
  private readonly api = inject(CheckoutApi);
  private readonly notify = inject(NotifyService);

  protected readonly orders = signal<OrderSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly page = signal(0);
  protected readonly last = signal(true);
  protected readonly labels = ORDER_STATUS_LABELS;
  protected readonly paymentLabels = PAYMENT_STATUS_LABELS;

  ngOnInit(): void {
    this.load(0);
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.api.orders(page, 10).subscribe({
      next: (p) => {
        this.orders.update((list) => (page === 0 ? p.content : [...list, ...p.content]));
        this.page.set(p.page);
        this.last.set(p.last);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Your orders could not be loaded.');
      },
    });
  }
}
