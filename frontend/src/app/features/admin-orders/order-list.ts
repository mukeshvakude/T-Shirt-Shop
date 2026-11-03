import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AdminConsoleApi } from '../../core/api/admin-console.api';
import { AdminOrderRow, STATUS_LABELS } from '../../core/models/admin.models';
import { NotifyService } from '../../core/notify.service';
import { InrPipe } from '../../shared/inr.pipe';

const STATUSES = ['', 'PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'OUT_FOR_DELIVERY', 'DELIVERED', 'CANCELLED'];

/** All orders with status filter and search; filters live in the URL. */
@Component({
  selector: 'app-admin-order-list',
  imports: [RouterLink, DatePipe, InrPipe, MatProgressBarModule],
  template: `
    <div class="page">
      <header class="page-header">
        <div><h1>Orders</h1><p>{{ total() }} order{{ total() === 1 ? '' : 's' }}{{ status() ? ' · ' + (labels[status()] ?? status()) : '' }}</p></div>
      </header>
      <section class="card table-card">
        <form class="filters" (submit)="$event.preventDefault(); apply({ q: q.value, page: 0 })">
          <input #q type="search" placeholder="Order number, name, email or phone" [value]="query()" aria-label="Search orders" />
          <select [value]="status()" (change)="apply({ status: $any($event.target).value, page: 0 })" aria-label="Status">
            @for (s of statuses; track s) { <option [value]="s" [selected]="s === status()">{{ s ? labels[s] : 'All statuses' }}</option> }
          </select>
          <label><input type="checkbox" [checked]="review()" (change)="apply({ needsReview: $any($event.target).checked, page: 0 })" /> Payment needs review</label>
        </form>
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>Order</th><th>Customer</th><th>Placed</th><th class="num">Items</th><th>Status</th><th>Payment</th><th class="num">Total</th></tr></thead>
            <tbody>
              @for (o of rows(); track o.orderNumber) {
                <tr class="link" [routerLink]="['/admin/orders', o.orderNumber]">
                  <td><strong>{{ o.orderNumber }}</strong></td>
                  <td>{{ o.customerName }}<small>{{ o.customerEmail }}</small></td>
                  <td>{{ o.placedAt | date: 'd MMM y, h:mm a' }}</td>
                  <td class="num">{{ o.itemCount }}</td>
                  <td><span class="status" [attr.data-s]="o.status">{{ labels[o.status] ?? o.status }}</span></td>
                  <td>{{ o.paymentMethod === 'COD' ? 'COD' : 'Online' }}
                    <span class="status" [attr.data-s]="o.paymentStatus">{{ labels[o.paymentStatus] ?? o.paymentStatus }}</span>
                    @if (o.needsReview) { <span class="flag" title="Payment needs review">!</span> }</td>
                  <td class="num">{{ o.total | inr }}</td>
                </tr>
              } @empty { <tr><td colspan="7" class="muted">No orders match.</td></tr> }
            </tbody>
          </table>
        </div>
        <div class="pager">
          <span class="muted">Page {{ page() + 1 }} of {{ pages() || 1 }}</span>
          <button type="button" (click)="apply({ page: page() - 1 })" [disabled]="page() === 0">Previous</button>
          <button type="button" (click)="apply({ page: page() + 1 })" [disabled]="page() + 1 >= pages()">Next</button>
        </div>
      </section>
    </div>
  `,
  styles: `.table-card { padding: 0; overflow: hidden; } td .status { margin-left: 4px; }`,
})
export class AdminOrderList implements OnInit {
  private readonly api = inject(AdminConsoleApi);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly statuses = STATUSES;
  protected readonly labels = STATUS_LABELS;
  protected readonly rows = signal<AdminOrderRow[]>([]);
  protected readonly total = signal(0);
  protected readonly pages = signal(0);
  protected readonly loading = signal(false);
  protected readonly status = signal('');
  protected readonly query = signal('');
  protected readonly review = signal(false);
  protected readonly page = signal(0);

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((m) => {
      this.status.set(m.get('status') ?? '');
      this.query.set(m.get('q') ?? '');
      this.review.set(m.get('needsReview') === 'true');
      this.page.set(Number(m.get('page') ?? 0) || 0);
      this.load();
    });
  }

  protected apply(change: { status?: string; q?: string; needsReview?: boolean; page?: number }): void {
    const next = { status: this.status(), q: this.query(), needsReview: this.review(), page: this.page(), ...change };
    void this.router.navigate([], {
      queryParams: { status: next.status || null, q: next.q || null, needsReview: next.needsReview || null, page: next.page || null },
    });
  }

  private load(): void {
    this.loading.set(true);
    this.api.orders({ status: this.status(), q: this.query(), needsReview: this.review(), page: this.page() }).subscribe({
      next: (p) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.pages.set(p.totalPages);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Orders could not be loaded.');
      },
    });
  }
}
