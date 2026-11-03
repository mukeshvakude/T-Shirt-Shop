import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AdminConsoleApi } from '../../core/api/admin-console.api';
import { AdminPaymentDetail, AdminPaymentRow, STATUS_LABELS } from '../../core/models/admin.models';
import { NotifyService } from '../../core/notify.service';
import { InrPipe } from '../../shared/inr.pipe';

const STATUSES = ['', 'CREATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED'];

/** Payment attempts with their full gateway log; refunds and review flags. */
@Component({
  selector: 'app-admin-payment-list',
  imports: [RouterLink, DatePipe, InrPipe, FormsModule, MatButtonModule, MatProgressBarModule],
  template: `
    <div class="page">
      <header class="page-header">
        <div><h1>Payments</h1><p>Online payments through Razorpay and cash collected on delivery. Refunds go back to the customer's original payment method.</p></div>
      </header>
      <section class="card table-card">
        <form class="filters" (submit)="$event.preventDefault(); apply({ q: q.value, page: 0 })">
          <input #q type="search" placeholder="Order number or Razorpay id" [value]="query()" aria-label="Search payments" />
          <select [value]="status()" (change)="apply({ status: $any($event.target).value, page: 0 })" aria-label="Status">
            @for (s of statuses; track s) { <option [value]="s" [selected]="s === status()">{{ s ? labels[s] : 'All statuses' }}</option> }
          </select>
          <label><input type="checkbox" [checked]="review()" (change)="apply({ needsReview: $any($event.target).checked, page: 0 })" /> Needs review</label>
        </form>
        @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
        <div class="table-scroll">
          <table class="data-table">
            <thead><tr><th>Created</th><th>Order</th><th>Provider</th><th>Status</th><th>Razorpay payment</th><th>Method</th><th class="num">Amount</th><th class="num">Refunded</th></tr></thead>
            <tbody>
              @for (p of rows(); track p.id) {
                <tr class="link" (click)="open(p.id)">
                  <td>{{ p.createdAt | date: 'd MMM, h:mm a' }}</td>
                  <td><a [routerLink]="['/admin/orders', p.orderNumber]" (click)="$event.stopPropagation()">{{ p.orderNumber }}</a><small>attempt {{ p.attemptNo }}</small></td>
                  <td>{{ p.provider }}</td>
                  <td><span class="status" [attr.data-s]="p.status">{{ labels[p.status] ?? p.status }}</span>
                    @if (p.needsReview) { <span class="flag" title="Needs review">!</span> }</td>
                  <td>{{ p.razorpayPaymentId || '—' }}<small>{{ p.razorpayOrderId }}</small></td>
                  <td>{{ p.method || '—' }}<small>{{ p.methodDetail }}</small></td>
                  <td class="num">{{ p.amount | inr }}</td>
                  <td class="num">{{ p.refundedAmount > 0 ? (p.refundedAmount | inr) : '—' }}</td>
                </tr>
              } @empty { <tr><td colspan="8" class="muted">No payments match.</td></tr> }
            </tbody>
          </table>
        </div>
        <div class="pager">
          <span class="muted">Page {{ page() + 1 }} of {{ pages() || 1 }}</span>
          <button type="button" (click)="apply({ page: page() - 1 })" [disabled]="page() === 0">Previous</button>
          <button type="button" (click)="apply({ page: page() + 1 })" [disabled]="page() + 1 >= pages()">Next</button>
        </div>
      </section>

      @if (selected(); as d) {
        <section class="card detail" aria-label="Payment details">
          <header>
            <h2>Payment {{ d.payment.razorpayPaymentId || '#' + d.payment.id }} <span class="status" [attr.data-s]="d.payment.status">{{ labels[d.payment.status] }}</span></h2>
            <button mat-button type="button" (click)="selected.set(null)">Close</button>
          </header>
          @if (d.payment.needsReview) {
            <p class="warn">Flagged for review. Check the log below (for example an amount mismatch or a refund that could not be made), then clear the flag.
              <button mat-stroked-button type="button" (click)="reviewed(d.payment.id)" [disabled]="busy()">Mark reviewed</button></p>
          }
          @if (d.payment.failureReason) { <p class="muted">Failure: {{ d.payment.failureReason }}</p> }
          <table class="data-table">
            <thead><tr><th>When</th><th>Event</th><th>Status</th><th>Source</th><th>Reference</th><th class="num">Amount</th></tr></thead>
            <tbody>
              @for (t of d.transactions; track $index) {
                <tr><td>{{ t.at | date: 'd MMM, h:mm:ss a' }}</td><td>{{ t.type }}</td><td>{{ t.status }}</td><td>{{ t.source }}</td>
                  <td>{{ t.reference || '—' }}</td><td class="num">{{ t.amount !== null ? (t.amount | inr) : '' }}</td></tr>
              }
            </tbody>
          </table>
          @if (d.payment.provider === 'RAZORPAY' && d.payment.status === 'SUCCESS' && d.payment.refundedAmount === 0) {
            <form class="refund" (submit)="$event.preventDefault(); refund(d.payment.id)">
              <label>Refund {{ d.payment.amount | inr }} in full. Reason
                <input name="reason" [(ngModel)]="reason" required maxlength="200" placeholder="e.g. Returned: size too small" /></label>
              <button mat-flat-button type="submit" class="danger" [disabled]="busy() || !reason.trim()">Refund</button>
            </form>
          }
        </section>
      }
    </div>
  `,
  styles: `
    .table-card { padding: 0; overflow: hidden; }
    .detail { margin-top: 16px; header { display: flex; justify-content: space-between; align-items: center; }
      h2 { font-size: 16px; margin: 0; display: flex; gap: 8px; align-items: center; } }
    .warn { background: #fff4d6; color: #7a5300; padding: 10px 12px; border-radius: 8px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
    .refund { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; margin-top: 16px;
      label { display: grid; gap: 4px; font-size: 13px; font-weight: 600; flex: 1; max-width: 460px; }
      input { height: 38px; padding: 0 10px; border: 1px solid #d6d6d6; border-radius: 8px; font: inherit; } }
    .danger { --mat-button-filled-container-color: var(--shop-danger); }
  `,
})
export class AdminPaymentList implements OnInit {
  private readonly api = inject(AdminConsoleApi);
  private readonly notify = inject(NotifyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly statuses = STATUSES;
  protected readonly labels = STATUS_LABELS;
  protected readonly rows = signal<AdminPaymentRow[]>([]);
  protected readonly pages = signal(0);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  protected readonly status = signal('');
  protected readonly query = signal('');
  protected readonly review = signal(false);
  protected readonly page = signal(0);
  protected readonly selected = signal<AdminPaymentDetail | null>(null);
  protected reason = '';

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

  protected open(id: number): void {
    this.reason = '';
    this.api.payment(id).subscribe({ next: (d) => this.selected.set(d), error: (e: unknown) => this.notify.error(e) });
  }

  protected async refund(id: number): Promise<void> {
    if (!confirm('Refund this payment in full? This cannot be undone.')) return;
    this.busy.set(true);
    try {
      const d = await firstValueFrom(this.api.refund(id, this.reason.trim()));
      this.selected.set(d);
      if (d.payment.status === 'REFUNDED') this.notify.success('Refund started. The customer usually sees it in 5 to 7 working days.');
      else this.notify.error('Razorpay could not take the refund right now. It is flagged; try again later.');
      this.load();
    } catch (error) {
      this.notify.error(error, 'The refund could not be made.');
    } finally {
      this.busy.set(false);
    }
  }

  protected async reviewed(id: number): Promise<void> {
    this.busy.set(true);
    try {
      this.selected.set(await firstValueFrom(this.api.reviewed(id)));
      this.load();
    } catch (error) {
      this.notify.error(error);
    } finally {
      this.busy.set(false);
    }
  }

  private load(): void {
    this.loading.set(true);
    this.api.payments({ status: this.status(), q: this.query(), needsReview: this.review(), page: this.page() }).subscribe({
      next: (p) => {
        this.rows.set(p.content);
        this.pages.set(p.totalPages);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Payments could not be loaded.');
      },
    });
  }
}
