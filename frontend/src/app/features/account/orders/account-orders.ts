import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AccountApi } from '../../../core/api/account.api';
import { ORDER_STATUS_LABELS, RecentOrder } from '../../../core/models/account.models';

/**
 * Order list for the customer. Shows the latest orders from the dashboard API; full order
 * details, tracking, invoices and cancellation are added with order management.
 */
@Component({
  selector: 'app-account-orders',
  imports: [RouterLink, CurrencyPipe, DatePipe],
  styleUrl: '../account-pages.scss',
  template: `
    <header class="page-head">
      <h1>Orders</h1>
      <p>Your recent orders and where they are.</p>
    </header>
    <section class="panel">
      @if (loading()) {
        <p class="muted">Loading…</p>
      } @else if (orders().length === 0) {
        <div class="empty">
          <span class="material-symbols-outlined">package_2</span>
          <p>No orders yet. When you place an order, you will see its status, payment and delivery updates here.</p>
          <a class="btn" routerLink="/shop">Start shopping</a>
        </div>
      } @else {
        <div class="table-wrap">
          <table>
            <thead><tr><th>Order</th><th>Date</th><th>Items</th><th>Total</th><th>Status</th></tr></thead>
            <tbody>
              @for (o of orders(); track o.orderNumber) {
                <tr>
                  <td class="strong">{{ o.orderNumber }}</td>
                  <td>{{ o.placedAt | date: 'd MMM y' }}</td>
                  <td>{{ o.itemCount }}</td>
                  <td>{{ o.grandTotal | currency: 'INR' : 'symbol' : '1.0-2' }}</td>
                  <td><span class="pill" [attr.data-status]="o.orderStatus">{{ labels[o.orderStatus] ?? o.orderStatus }}</span></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class AccountOrders implements OnInit {
  private readonly api = inject(AccountApi);

  protected readonly orders = signal<RecentOrder[]>([]);
  protected readonly loading = signal(true);
  protected readonly labels = ORDER_STATUS_LABELS;

  ngOnInit(): void {
    this.api.dashboard().subscribe({
      next: (d) => {
        this.orders.set(d.recentOrders);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
