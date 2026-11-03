import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { AdminConsoleApi } from '../../core/api/admin-console.api';
import { AdminOrderDetail, NEXT_STATUS_ACTIONS, STATUS_LABELS } from '../../core/models/admin.models';
import { NotifyService } from '../../core/notify.service';
import { InrPipe } from '../../shared/inr.pipe';

/** One order for the shop: items, customer, payments, status history and the next steps. */
@Component({
  selector: 'app-admin-order-detail',
  imports: [RouterLink, DatePipe, InrPipe, FormsModule, MatButtonModule, MatProgressBarModule],
  template: `
    <div class="page">
      <a routerLink="/admin/orders" class="back"><span class="material-symbols-outlined">arrow_back</span>Orders</a>
      @if (!detail()) { <mat-progress-bar mode="indeterminate" /> }
      @if (detail(); as d) {
        <header class="page-header">
          <div>
            <h1>{{ d.order.orderNumber }}</h1>
            <p>Placed {{ d.order.placedAt | date: 'd MMM y, h:mm a' }} ·
              <span class="status" [attr.data-s]="d.order.status">{{ labels[d.order.status] }}</span>
              {{ d.order.paymentMethod === 'COD' ? 'Cash on Delivery' : 'Online' }}
              <span class="status" [attr.data-s]="d.order.paymentStatus">{{ labels[d.order.paymentStatus] }}</span></p>
          </div>
          @if (d.order.invoiceAvailable) {
            <button mat-stroked-button type="button" (click)="invoice()"><span class="material-symbols-outlined">download</span> Invoice</button>
          }
        </header>

        @if (d.nextStatuses.length) {
          <section class="card actions">
            <h2>Next step</h2>
            @if (shipping()) {
              <form class="ship" (submit)="$event.preventDefault(); move('SHIPPED')">
                <label>Courier <input name="courier" [(ngModel)]="courier" required maxlength="80" placeholder="e.g. Delhivery" /></label>
                <label>Tracking number <input name="tracking" [(ngModel)]="tracking" required maxlength="80" /></label>
                <button mat-flat-button type="submit" [disabled]="busy() || !courier.trim() || !tracking.trim()">Mark shipped</button>
                <button mat-button type="button" (click)="shipping.set(false)">Back</button>
              </form>
            } @else {
              <div class="buttons">
                @for (s of d.nextStatuses; track s) {
                  <button mat-stroked-button type="button" [class.danger]="s === 'CANCELLED'" [class.primary]="s !== 'CANCELLED'"
                          [disabled]="busy()" (click)="act(s)">{{ actions[s] }}</button>
                }
              </div>
              <label class="note">Note for the customer's order history <small>(optional)</small>
                <input [(ngModel)]="note" maxlength="200" placeholder="e.g. Packed with care" /></label>
            }
          </section>
        }

        <div class="grid">
          <section class="card">
            <h2>Items</h2>
            <table class="data-table">
              <thead><tr><th>Item</th><th>Size / colour</th><th class="num">Qty</th><th class="num">Unit</th><th class="num">Total</th></tr></thead>
              <tbody>
                @for (i of d.order.items; track $index) {
                  <tr>
                    <td>{{ i.name }}<small>{{ i.sku }}</small>
                      @if (i.designCode) { <small>Design <a routerLink="/admin/designs">{{ i.designCode }}</a>
                        · T-Shirt {{ i.unitBasePrice | inr }} + {{ i.unitCustomizationCharge | inr }}</small> }</td>
                    <td>{{ i.sizeCode }} / {{ i.colorName }}</td>
                    <td class="num">{{ i.quantity }}</td>
                    <td class="num">{{ i.unitBasePrice + i.unitCustomizationCharge | inr }}</td>
                    <td class="num">{{ i.lineTotal | inr }}</td>
                  </tr>
                }
              </tbody>
            </table>
            <div class="totals">
              <div><span>Items</span><span>{{ d.order.itemsTotal | inr }}</span></div>
              @if (d.order.customizationTotal > 0) { <div><span>Customization</span><span>{{ d.order.customizationTotal | inr }}</span></div> }
              <div><span>Shipping</span><span>{{ d.order.shipping | inr }}</span></div>
              @if (d.order.discount > 0) { <div><span>Discount ({{ d.order.couponCode }})</span><span>−{{ d.order.discount | inr }}</span></div> }
              <div class="total"><span>Total</span><strong>{{ d.order.total | inr }}</strong></div>
            </div>
          </section>

          <aside class="side">
            <section class="card">
              <h2>Customer</h2>
              <p><a [routerLink]="['/admin/customers']" [queryParams]="{ id: d.customerId }">{{ d.order.contact.name }}</a><br />
                {{ d.order.contact.email }}<br />{{ d.order.contact.phone }}</p>
              <h2>Ship to</h2>
              <p>{{ d.order.shippingAddress.fullName }}<br />{{ d.order.shippingAddress.line1 }}
                @if (d.order.shippingAddress.line2) { , {{ d.order.shippingAddress.line2 }} }<br />
                @if (d.order.shippingAddress.landmark) { {{ d.order.shippingAddress.landmark }}<br /> }
                {{ d.order.shippingAddress.city }}, {{ d.order.shippingAddress.state }} {{ d.order.shippingAddress.pincode }}<br />
                {{ d.order.shippingAddress.phone }}</p>
              @if (d.order.courierName) { <p><strong>{{ d.order.courierName }}</strong> · {{ d.order.trackingNumber }}</p> }
              @if (d.order.note) { <p class="muted">Customer note: {{ d.order.note }}</p> }
            </section>
            <section class="card">
              <h2>History</h2>
              <ol class="history">
                @for (h of d.order.history; track $index) {
                  <li><strong>{{ labels[h.status] ?? h.status }}</strong> <small>{{ h.at | date: 'd MMM, h:mm a' }}</small>
                    @if (h.note) { <span>{{ h.note }}</span> }</li>
                }
              </ol>
            </section>
          </aside>
        </div>

        @if (d.payments.length) {
          <section class="card">
            <h2>Payments</h2>
            <table class="data-table">
              <thead><tr><th>#</th><th>Provider</th><th>Status</th><th>Razorpay payment</th><th>Method</th><th class="num">Amount</th><th class="num">Refunded</th></tr></thead>
              <tbody>
                @for (p of d.payments; track p.id) {
                  <tr class="link" [routerLink]="['/admin/payments']" [queryParams]="{ q: p.razorpayPaymentId || p.razorpayOrderId || d.order.orderNumber }">
                    <td>{{ p.attemptNo }}</td><td>{{ p.provider }}</td>
                    <td><span class="status" [attr.data-s]="p.status">{{ labels[p.status] ?? p.status }}</span>
                      @if (p.needsReview) { <span class="flag" title="Needs review">!</span> }</td>
                    <td>{{ p.razorpayPaymentId || '—' }}<small>{{ p.razorpayOrderId }}</small></td>
                    <td>{{ p.method || '—' }}<small>{{ p.methodDetail }}</small></td>
                    <td class="num">{{ p.amount | inr }}</td><td class="num">{{ p.refundedAmount | inr }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </section>
        }
      }
    </div>
  `,
  styles: `
    .back { display: inline-flex; align-items: center; gap: 4px; color: inherit; text-decoration: none; font-weight: 600; margin-bottom: 12px; }
    h2 { font-size: 16px; margin: 0 0 10px; }
    .page-header p { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .actions { margin-bottom: 16px; }
    .buttons { display: flex; flex-wrap: wrap; gap: 10px; }
    .danger { color: #a1271c; }
    .primary { background: #111; color: #fff; }
    .note, .ship label { display: grid; gap: 4px; font-size: 13px; font-weight: 600; margin-top: 12px; max-width: 420px;
      input { height: 38px; padding: 0 10px; border: 1px solid #d6d6d6; border-radius: 8px; font: inherit; }
      small { font-weight: 400; color: #777; } }
    .ship { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; label { margin-top: 0; } }
    .grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 16px; margin-bottom: 16px; }
    .side { display: grid; gap: 16px; align-content: start; p { margin: 0 0 12px; line-height: 1.55; font-size: 14px; } }
    .totals { margin-top: 12px; max-width: 320px; margin-left: auto;
      div { display: flex; justify-content: space-between; padding: 4px 0; font-size: 14px; }
      .total { border-top: 1px solid #eee; margin-top: 4px; padding-top: 8px; } }
    .history { list-style: none; padding: 0; margin: 0; display: grid; gap: 10px;
      li { border-left: 2px solid #111; padding-left: 12px; font-size: 13px; display: grid; }
      small { color: #777; } span { color: #444; } }
    @media (max-width: 1000px) { .grid { grid-template-columns: minmax(0, 1fr); } }
  `,
})
export class AdminOrderDetailPage implements OnInit {
  private readonly api = inject(AdminConsoleApi);
  private readonly notify = inject(NotifyService);

  readonly number = input.required<string>();
  protected readonly detail = signal<AdminOrderDetail | null>(null);
  protected readonly busy = signal(false);
  protected readonly shipping = signal(false);
  protected readonly labels = STATUS_LABELS;
  protected readonly actions = NEXT_STATUS_ACTIONS;
  protected courier = '';
  protected tracking = '';
  protected note = '';

  ngOnInit(): void {
    this.load();
  }

  protected act(status: string): void {
    if (status === 'SHIPPED') {
      this.shipping.set(true);
      return;
    }
    if (status === 'CANCELLED' && !confirm('Cancel this order? Stock goes back, and a paid online order is refunded in full.')) {
      return;
    }
    void this.move(status);
  }

  protected async move(status: string): Promise<void> {
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.updateStatus(this.number(), {
        status,
        courierName: status === 'SHIPPED' ? this.courier.trim() : undefined,
        trackingNumber: status === 'SHIPPED' ? this.tracking.trim() : undefined,
        note: this.note.trim() || undefined,
      }));
      this.notify.success(`Order marked ${STATUS_LABELS[status]?.toLowerCase() ?? status}.`);
      this.note = '';
      this.shipping.set(false);
      this.load();
    } catch (error) {
      this.notify.error(error, 'The order could not be updated.');
    } finally {
      this.busy.set(false);
    }
  }

  protected async invoice(): Promise<void> {
    try {
      const blob = await firstValueFrom(this.api.invoice(this.number()));
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'INV-' + this.number().slice(3) + '.pdf';
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (error) {
      this.notify.error(error, 'The invoice could not be downloaded.');
    }
  }

  private load(): void {
    this.api.order(this.number()).subscribe({
      next: (d) => this.detail.set(d),
      error: (err: unknown) => this.notify.error(err, 'The order could not be loaded.'),
    });
  }
}
