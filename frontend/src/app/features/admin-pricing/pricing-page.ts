import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';

import { PricingApi } from '../../core/api/pricing.api';
import { AdminCharge, CHARGE_GROUPS } from '../../core/models/pricing.models';
import { NotifyService } from '../../core/notify.service';

interface Row extends AdminCharge {
  draftAmount: string;
  draftActive: boolean;
  saving: boolean;
}

/**
 * Admin price list for custom T-Shirts: extra charges per style, print area, text, premium print,
 * size and colour, plus shipping. Changes apply to new price calculations immediately.
 */
@Component({
  selector: 'app-pricing-page',
  imports: [ReactiveFormsModule, MatButtonModule, MatProgressBarModule, MatSlideToggleModule, CurrencyPipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Pricing</h1>
          <p>Extra charges for custom T-Shirts and shipping. The blank T-Shirt price itself is set on each custom product.
            All prices include GST.</p>
        </div>
      </header>

      <section class="card">
        <h2>Shipping</h2>
        <form class="shipping" [formGroup]="shippingForm" (ngSubmit)="saveShipping()">
          <label>Shipping charge (INR)
            <input type="number" min="0" step="1" formControlName="flatCharge" />
          </label>
          <label>Free shipping from (INR)
            <input type="number" min="0" step="1" formControlName="freeThreshold" />
          </label>
          <button mat-flat-button type="submit" [disabled]="shippingForm.invalid || shippingForm.pristine || savingShipping()">Save shipping</button>
        </form>
        <p class="muted">Orders below the free-shipping amount pay the shipping charge.</p>
      </section>

      @if (loading()) { <mat-progress-bar mode="indeterminate" /> }
      @for (g of groups(); track g.type) {
        <section class="card">
          <h2>{{ g.title }}</h2>
          <p class="muted">{{ g.hint }}</p>
          <div class="table-scroll">
            <table>
              <thead><tr><th>Choice</th><th>Charge (INR)</th><th>Active</th><th></th></tr></thead>
              <tbody>
                @for (r of g.rows; track r.code) {
                  <tr [class.off]="!r.draftActive">
                    <td><strong>{{ r.label }}</strong> <span class="code">{{ r.code }}</span></td>
                    <td>
                      <input type="number" min="0" step="1" [value]="r.draftAmount" [attr.aria-label]="r.label + ' charge'"
                             (input)="edit(r, { draftAmount: $any($event.target).value })" />
                    </td>
                    <td>
                      <mat-slide-toggle [checked]="r.draftActive" [attr.aria-label]="r.label + ' active'"
                                        (change)="edit(r, { draftActive: $event.checked })" />
                    </td>
                    <td class="actions">
                      @if (changed(r)) {
                        <button mat-flat-button type="button" (click)="save(r)" [disabled]="r.saving || !valid(r)">Save</button>
                      } @else if (r.id && r.active && r.amount > 0) {
                        <span class="muted">+{{ r.amount | currency: 'INR' : 'symbol' : '1.0-2' }}</span>
                      } @else {
                        <span class="muted">No charge</span>
                      }
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
  styles: `
    .card { margin-bottom: 20px; }
    h2 { font-size: 18px; margin: 0 0 4px; }
    .muted { color: #666; font-size: 13px; margin: 4px 0 12px; }
    .shipping { display: flex; gap: 16px; align-items: flex-end; flex-wrap: wrap; margin-top: 12px; }
    label { display: grid; gap: 6px; font-size: 13px; font-weight: 600; }
    input { width: 140px; height: 38px; padding: 0 10px; border: 1px solid #d6d6d6; border-radius: 8px; font: inherit; }
    .table-scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #eee; font-size: 14px; }
    td input { width: 110px; height: 34px; }
    .code { color: #888; font-size: 12px; margin-left: 6px; }
    tr.off td:first-child { opacity: 0.55; }
    .actions { width: 120px; text-align: right; }
  `,
})
export class PricingPage implements OnInit {
  private readonly api = inject(PricingApi);
  private readonly notify = inject(NotifyService);

  protected readonly rows = signal<Row[]>([]);
  protected readonly loading = signal(true);
  protected readonly savingShipping = signal(false);
  protected readonly groups = computed(() =>
    CHARGE_GROUPS.map((g) => ({ ...g, rows: this.rows().filter((r) => r.type === g.type) })).filter((g) => g.rows.length),
  );

  protected readonly shippingForm = inject(FormBuilder).nonNullable.group({
    flatCharge: [0, [Validators.required, Validators.min(0), Validators.max(10000)]],
    freeThreshold: [0, [Validators.required, Validators.min(0), Validators.max(1000000)]],
  });

  ngOnInit(): void {
    this.api.adminCharges().subscribe({
      next: (list) => {
        this.rows.set(list.map((c) => this.toRow(c)));
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Prices could not be loaded.');
      },
    });
    this.api.shipping().subscribe({
      next: (s) => this.shippingForm.reset(s),
      error: (err: unknown) => this.notify.error(err),
    });
  }

  protected edit(row: Row, patch: Partial<Row>): void {
    this.rows.update((rows) => rows.map((r) => (r === row ? { ...r, ...patch } : r)));
  }

  protected changed(r: Row): boolean {
    return Number(r.draftAmount) !== r.amount || r.draftActive !== r.active;
  }

  protected valid(r: Row): boolean {
    const n = Number(r.draftAmount);
    return r.draftAmount !== '' && Number.isFinite(n) && n >= 0 && n <= 100000;
  }

  protected save(row: Row): void {
    this.edit(row, { saving: true });
    const current = this.rows().find((r) => r.type === row.type && r.code === row.code)!;
    this.api
      .saveCharge({ type: row.type, code: row.code, amount: Number(current.draftAmount), active: current.draftActive })
      .subscribe({
        next: (saved) => {
          this.rows.update((rows) => rows.map((r) => (r.type === saved.type && r.code === saved.code ? this.toRow(saved) : r)));
          this.notify.success(`${saved.label}: ${saved.active && saved.amount > 0 ? 'charge saved' : 'no charge'}.`);
        },
        error: (err: unknown) => {
          this.rows.update((rows) => rows.map((r) => (r.type === row.type && r.code === row.code ? { ...r, saving: false } : r)));
          this.notify.error(err, 'The charge could not be saved.');
        },
      });
  }

  protected saveShipping(): void {
    this.savingShipping.set(true);
    this.api.updateShipping(this.shippingForm.getRawValue()).subscribe({
      next: (s) => {
        this.shippingForm.reset(s);
        this.savingShipping.set(false);
        this.notify.success('Shipping saved.');
      },
      error: (err: unknown) => {
        this.savingShipping.set(false);
        this.notify.error(err, 'Shipping could not be saved.');
      },
    });
  }

  private toRow(c: AdminCharge): Row {
    // A choice without a charge row counts as active with no charge, so saving an amount switches it on.
    const active = c.id ? c.active : true;
    return { ...c, active, draftAmount: String(c.amount), draftActive: active, saving: false };
  }
}
