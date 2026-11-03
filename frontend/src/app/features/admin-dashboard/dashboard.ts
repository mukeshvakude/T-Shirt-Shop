import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';

import { AdminConsoleApi } from '../../core/api/admin-console.api';
import { Dashboard, STATUS_LABELS } from '../../core/models/admin.models';
import { NotifyService } from '../../core/notify.service';
import { InrPipe } from '../../shared/inr.pipe';

interface Bar {
  day: string;
  label: string;
  orders: number;
  sales: number;
  height: number;
}

/** Admin home: sales, work waiting, sales per day, top products and the latest orders. */
@Component({
  selector: 'app-admin-dashboard',
  imports: [RouterLink, DatePipe, InrPipe, MatProgressBarModule],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Dashboard</h1>
          <p>Sales count orders that are confirmed or further along (not waiting for payment, not cancelled). Amounts include GST.</p>
        </div>
      </header>
      @if (!data()) { <mat-progress-bar mode="indeterminate" /> }
      @if (data(); as d) {
        <section class="kpis">
          @for (k of periods(); track k.label) {
            <div class="card kpi">
              <small>{{ k.label }}</small>
              <strong>{{ k.p.sales | inr }}</strong>
              <span class="muted">{{ k.p.orders }} order{{ k.p.orders === 1 ? '' : 's' }} · avg {{ k.p.averageOrder | inr }}</span>
            </div>
          }
        </section>

        <section class="todo">
          <a class="card tile" routerLink="/admin/orders" [queryParams]="{ status: 'CONFIRMED' }">
            <span class="material-symbols-outlined">package_2</span><strong>{{ d.toShip }}</strong><small>To prepare and ship</small></a>
          <a class="card tile" routerLink="/admin/orders" [queryParams]="{ status: 'PENDING' }">
            <span class="material-symbols-outlined">schedule</span><strong>{{ d.awaitingPayment }}</strong><small>Awaiting online payment</small></a>
          <a class="card tile" [class.alert]="d.paymentsNeedingReview" routerLink="/admin/payments" [queryParams]="{ needsReview: true }">
            <span class="material-symbols-outlined">report</span><strong>{{ d.paymentsNeedingReview }}</strong><small>Payments to review</small></a>
          <a class="card tile" [class.alert]="d.lowStockSkus" routerLink="/admin/inventory">
            <span class="material-symbols-outlined">inventory_2</span><strong>{{ d.lowStockSkus }}</strong><small>Low-stock SKUs</small></a>
          <a class="card tile" routerLink="/admin/messages" [queryParams]="{ status: 'NEW' }">
            <span class="material-symbols-outlined">mail</span><strong>{{ d.newMessages }}</strong><small>New messages</small></a>
          <div class="card tile static">
            <span class="material-symbols-outlined">person_add</span><strong>{{ d.newCustomers30Days }}</strong><small>New customers, 30 days</small></div>
        </section>

        <div class="grid">
          <section class="card chart-card">
            <h2>Sales per day <small class="muted">last 14 days</small></h2>
            @if (bars().length) {
              <div class="chart" role="img" [attr.aria-label]="chartLabel()" (mouseleave)="hover.set(null)">
                <div class="plot">
                  @for (b of bars(); track b.day) {
                    <button type="button" class="col" (mouseenter)="hover.set(b)" (focus)="hover.set(b)" (blur)="hover.set(null)"
                            [attr.aria-label]="b.label + ': ' + b.orders + ' orders'">
                      <span class="bar" [style.height.%]="b.height"></span>
                    </button>
                  }
                </div>
                <div class="axis">
                  @for (b of bars(); track b.day) { <span>{{ b.day | date: 'd' }}</span> }
                </div>
                @if (hover(); as h) {
                  <div class="tip" role="status"><strong>{{ h.day | date: 'EEE d MMM' }}</strong>
                    <span>{{ h.sales | inr }}</span><small>{{ h.orders }} order{{ h.orders === 1 ? '' : 's' }}</small></div>
                }
              </div>
              <details class="as-table"><summary>Show as table</summary>
                <table class="data-table">
                  <thead><tr><th>Day</th><th class="num">Orders</th><th class="num">Sales</th></tr></thead>
                  <tbody>@for (b of bars(); track b.day) { <tr><td>{{ b.day | date: 'EEE d MMM' }}</td><td class="num">{{ b.orders }}</td><td class="num">{{ b.sales | inr }}</td></tr> }</tbody>
                </table>
              </details>
            } @else {
              <p class="muted">No sales in the last 14 days yet.</p>
            }
          </section>

          <section class="card">
            <h2>Top products <small class="muted">30 days</small></h2>
            @if (d.topProducts.length) {
              <table class="data-table">
                <thead><tr><th>Product</th><th class="num">Sold</th><th class="num">Sales</th></tr></thead>
                <tbody>
                  @for (t of d.topProducts; track t.productId) {
                    <tr><td>{{ t.name }}</td><td class="num">{{ t.quantity }}</td><td class="num">{{ t.sales | inr }}</td></tr>
                  }
                </tbody>
              </table>
            } @else { <p class="muted">No sales yet.</p> }
            <h2 class="space">Orders by status</h2>
            <div class="statuses">
              @for (s of d.ordersByStatus; track s.status) {
                <a routerLink="/admin/orders" [queryParams]="{ status: s.status }"><span class="status" [attr.data-s]="s.status">{{ labels[s.status] ?? s.status }}</span> {{ s.count }}</a>
              }
            </div>
            @if (d.refunded30Days > 0) { <p class="muted small">Refunded in the last 30 days: {{ d.refunded30Days | inr }}</p> }
          </section>
        </div>

        <section class="card table-card">
          <h2>Latest orders</h2>
          <div class="table-scroll">
            <table class="data-table">
              <thead><tr><th>Order</th><th>Customer</th><th>Placed</th><th>Status</th><th>Payment</th><th class="num">Total</th></tr></thead>
              <tbody>
                @for (o of d.recentOrders; track o.orderNumber) {
                  <tr class="link" [routerLink]="['/admin/orders', o.orderNumber]">
                    <td><strong>{{ o.orderNumber }}</strong></td>
                    <td>{{ o.customerName }}<small>{{ o.customerEmail }}</small></td>
                    <td>{{ o.placedAt | date: 'd MMM, h:mm a' }}</td>
                    <td><span class="status" [attr.data-s]="o.status">{{ labels[o.status] ?? o.status }}</span></td>
                    <td>{{ o.paymentMethod === 'COD' ? 'COD' : 'Online' }} · {{ labels[o.paymentStatus] ?? o.paymentStatus }}
                      @if (o.needsReview) { <span class="flag" title="Needs review">!</span> }</td>
                    <td class="num">{{ o.total | inr }}</td>
                  </tr>
                } @empty { <tr><td colspan="6" class="muted">No orders yet.</td></tr> }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    h2 { font-size: 16px; margin: 0 0 12px; small { font-weight: 400; font-size: 12px; } }
    .space { margin-top: 20px; }
    .small { font-size: 13px; margin: 12px 0 0; }
    .kpis { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; margin-bottom: 16px; }
    .kpi { display: grid; gap: 4px; strong { font-size: 28px; font-weight: 700; } small { color: #555; font-weight: 600; } }
    .todo { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; margin-bottom: 16px; }
    .tile { display: grid; gap: 2px; text-decoration: none; color: inherit; padding: 14px 16px;
      .material-symbols-outlined { color: #555; } strong { font-size: 24px; } small { color: #555; }
      &.alert { border-color: #f0b4ae; .material-symbols-outlined, strong { color: #a1271c; } }
      &:not(.static):hover { border-color: #111; } }
    .grid { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 16px; margin-bottom: 16px; }
    .chart { position: relative; }
    .plot { height: 180px; display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 2px; align-items: end;
      border-bottom: 1px solid #ddd; }
    .col { height: 100%; border: 0; background: none; padding: 0 3px; display: flex; align-items: flex-end; cursor: default;
      &:hover .bar, &:focus-visible .bar { background: #444; } &:focus-visible { outline: 2px solid #111; outline-offset: 1px; } }
    .bar { display: block; width: 100%; min-height: 2px; background: #1f1f1f; border-radius: 4px 4px 0 0; }
    .axis { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 2px; font-size: 11px; color: #777; text-align: center; padding-top: 4px; }
    .tip { position: absolute; top: 0; right: 0; background: #fff; border: 1px solid #ddd; border-radius: 8px; padding: 6px 10px;
      display: grid; font-size: 13px; box-shadow: 0 4px 12px rgb(0 0 0 / 8%); small { color: #666; } }
    .as-table { margin-top: 12px; font-size: 13px; summary { cursor: pointer; color: #555; } }
    .statuses { display: flex; flex-wrap: wrap; gap: 10px; a { text-decoration: none; color: inherit; font-size: 14px; } }
    .table-card h2 { padding: 0 0 4px; }
    @media (max-width: 1100px) { .todo { grid-template-columns: repeat(3, 1fr); } .grid { grid-template-columns: minmax(0, 1fr); } }
    @media (max-width: 640px) { .kpis { grid-template-columns: 1fr; } .todo { grid-template-columns: repeat(2, 1fr); } }
  `,
})
export class AdminDashboard implements OnInit {
  private readonly api = inject(AdminConsoleApi);
  private readonly notify = inject(NotifyService);

  protected readonly data = signal<Dashboard | null>(null);
  protected readonly hover = signal<Bar | null>(null);
  protected readonly labels = STATUS_LABELS;

  protected readonly periods = computed(() => {
    const d = this.data();
    return d ? [{ label: 'Today', p: d.today }, { label: 'Last 7 days', p: d.last7Days }, { label: 'Last 30 days', p: d.last30Days }] : [];
  });

  /** 14 days ending today; days without sales are shown as empty columns. */
  protected readonly bars = computed<Bar[]>(() => {
    const d = this.data();
    if (!d || d.salesByDay.length === 0) return [];
    const byDay = new Map(d.salesByDay.map((s) => [s.day, s]));
    const max = Math.max(...d.salesByDay.map((s) => s.sales), 1);
    const out: Bar[] = [];
    const today = new Date();
    for (let i = 13; i >= 0; i--) {
      const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - i);
      const key = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
      const s = byDay.get(key);
      out.push({ day: key, label: key, orders: s?.orders ?? 0, sales: s?.sales ?? 0, height: s ? (s.sales / max) * 100 : 0 });
    }
    return out;
  });

  protected readonly chartLabel = computed(() => {
    const total = this.bars().reduce((sum, b) => sum + b.sales, 0);
    return `Sales per day for the last 14 days, total ${Math.round(total)} rupees`;
  });

  ngOnInit(): void {
    this.api.dashboard().subscribe({
      next: (d) => this.data.set(d),
      error: (err: unknown) => this.notify.error(err, 'The dashboard could not be loaded.'),
    });
  }
}
