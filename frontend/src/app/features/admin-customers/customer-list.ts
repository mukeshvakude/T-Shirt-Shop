import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AdminConsoleApi } from '../../core/api/admin-console.api';
import { CustomerDetail, CustomerRow, STATUS_LABELS } from '../../core/models/admin.models';
import { NotifyService } from '../../core/notify.service';
import { InrPipe } from '../../shared/inr.pipe';

/** Customers with orders and spend; open one to see recent orders or block the account. */
@Component({
  selector: 'app-admin-customer-list',
  imports: [RouterLink, DatePipe, InrPipe, MatButtonModule, MatProgressBarModule],
  template: `
    <div class="page">
      <header class="page-header">
        <div><h1>Customers</h1><p>{{ total() }} customer account{{ total() === 1 ? '' : 's' }}. Spend counts confirmed orders.</p></div>
      </header>
      <section class="card table-card">
        <form class="filters" (submit)="$event.preventDefault(); search(q.value)">
          <input #q type="search" placeholder="Name, email or phone" aria-label="Search customers" />
          <button mat-stroked-button type="submit">Search</button>
        </form>
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>Customer</th><th>Phone</th><th>Joined</th><th>Last sign-in</th><th class="num">Orders</th><th class="num">Spent</th><th>Account</th></tr></thead>
            <tbody>
              @for (c of rows(); track c.id) {
                <tr class="link" (click)="open(c.id)">
                  <td><strong>{{ c.fullName }}</strong><small>{{ c.email }}</small></td>
                  <td>{{ c.phone || '—' }}</td>
                  <td>{{ c.joinedAt | date: 'd MMM y' }}</td>
                  <td>{{ c.lastLoginAt ? (c.lastLoginAt | date: 'd MMM y') : '—' }}</td>
                  <td class="num">{{ c.orders }}</td>
                  <td class="num">{{ c.spent | inr }}</td>
                  <td><span class="status" [attr.data-s]="c.enabled ? 'SUCCESS' : 'CANCELLED'">{{ c.enabled ? 'Active' : 'Blocked' }}</span></td>
                </tr>
              } @empty { <tr><td colspan="7" class="muted">No customers match.</td></tr> }
            </tbody>
          </table>
        </div>
        <div class="pager">
          <span class="muted">Page {{ page() + 1 }} of {{ pages() || 1 }}</span>
          <button type="button" (click)="go(page() - 1)" [disabled]="page() === 0">Previous</button>
          <button type="button" (click)="go(page() + 1)" [disabled]="page() + 1 >= pages()">Next</button>
        </div>
      </section>

      @if (selected(); as d) {
        <section class="card detail" aria-label="Customer details">
          <header>
            <h2>{{ d.customer.fullName }}</h2>
            <button mat-button type="button" (click)="selected.set(null)">Close</button>
          </header>
          <p>{{ d.customer.email }} · {{ d.customer.phone || 'no phone' }} · {{ d.addresses }} saved address{{ d.addresses === 1 ? '' : 'es' }} ·
            {{ d.designs }} saved design{{ d.designs === 1 ? '' : 's' }}</p>
          <table class="data-table">
            <thead><tr><th>Order</th><th>Placed</th><th>Status</th><th class="num">Total</th></tr></thead>
            <tbody>
              @for (o of d.orders; track o.orderNumber) {
                <tr class="link" [routerLink]="['/admin/orders', o.orderNumber]">
                  <td>{{ o.orderNumber }}</td><td>{{ o.placedAt | date: 'd MMM y' }}</td>
                  <td><span class="status" [attr.data-s]="o.status">{{ labels[o.status] ?? o.status }}</span></td><td class="num">{{ o.total | inr }}</td>
                </tr>
              } @empty { <tr><td colspan="4" class="muted">No orders yet.</td></tr> }
            </tbody>
          </table>
          <div class="account">
            @if (d.customer.enabled) {
              <button mat-stroked-button type="button" class="danger" (click)="setEnabled(d, false)" [disabled]="busy()">Block account</button>
              <small class="muted">Blocking signs the customer out everywhere and stops sign-in. Orders are not changed.</small>
            } @else {
              <button mat-flat-button type="button" (click)="setEnabled(d, true)" [disabled]="busy()">Unblock account</button>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .table-card { padding: 0; overflow: hidden; }
    .detail { margin-top: 16px; header { display: flex; justify-content: space-between; align-items: center; } h2 { font-size: 18px; margin: 0; } }
    .account { display: flex; gap: 12px; align-items: center; margin-top: 16px; flex-wrap: wrap; }
    .danger { color: var(--shop-danger); }
  `,
})
export class AdminCustomerList implements OnInit {
  private readonly api = inject(AdminConsoleApi);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);

  protected readonly labels = STATUS_LABELS;
  protected readonly rows = signal<CustomerRow[]>([]);
  protected readonly total = signal(0);
  protected readonly pages = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly selected = signal<CustomerDetail | null>(null);
  private query = '';

  ngOnInit(): void {
    this.load();
    const id = Number(this.route.snapshot.queryParamMap.get('id'));
    if (id) this.open(id);
  }

  protected search(q: string): void {
    this.query = q.trim();
    this.page.set(0);
    this.load();
  }

  protected go(page: number): void {
    this.page.set(page);
    this.load();
  }

  protected open(id: number): void {
    this.api.customer(id).subscribe({ next: (d) => this.selected.set(d), error: (e: unknown) => this.notify.error(e) });
  }

  protected async setEnabled(d: CustomerDetail, enabled: boolean): Promise<void> {
    if (!enabled && !confirm(`Block ${d.customer.fullName}? They will be signed out and cannot sign in until unblocked.`)) return;
    this.busy.set(true);
    try {
      this.selected.set(await firstValueFrom(this.api.setCustomerEnabled(d.customer.id, enabled)));
      this.notify.success(enabled ? 'Account unblocked.' : 'Account blocked.');
      this.load();
    } catch (error) {
      this.notify.error(error);
    } finally {
      this.busy.set(false);
    }
  }

  private load(): void {
    this.loading.set(true);
    this.api.customers(this.query, this.page()).subscribe({
      next: (p) => {
        this.rows.set(p.content);
        this.total.set(p.totalElements);
        this.pages.set(p.totalPages);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Customers could not be loaded.');
      },
    });
  }
}
