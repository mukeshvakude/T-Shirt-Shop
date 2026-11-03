import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

export interface CancelOrderData {
  orderNumber: string;
  paidOnline: boolean;
}

const REASONS = ['Ordered by mistake', 'Want a different size or colour', 'Found a better price elsewhere', 'Delivery is too slow', 'Other'];

/** Asks for confirmation and an optional reason before cancelling an order. Closes with the reason, or nothing. */
@Component({
  selector: 'app-cancel-order-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Cancel order {{ data.orderNumber }}?</h2>
    <mat-dialog-content>
      <p>{{ data.paidOnline
        ? 'Your payment will be refunded in full to the account you paid from. Banks usually show it within 5 to 7 working days.'
        : 'Nothing has been charged, so there is nothing to refund.' }}</p>
      <fieldset>
        <legend>Reason <small>(optional)</small></legend>
        @for (r of reasons; track r) {
          <label><input type="radio" name="reason" [value]="r" [checked]="reason() === r" (change)="reason.set(r)" /> {{ r }}</label>
        }
      </fieldset>
      @if (reason() === 'Other') {
        <textarea rows="2" maxlength="150" placeholder="Tell us more (optional)" (input)="other.set($any($event.target).value)"></textarea>
      }
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="undefined">Keep order</button>
      <button mat-flat-button class="danger" (click)="confirm()">Cancel order</button>
    </mat-dialog-actions>
  `,
  styles: `
    fieldset { border: 0; padding: 0; margin: 12px 0 0; display: grid; gap: 6px; }
    legend { font-weight: 600; margin-bottom: 6px; small { font-weight: 400; color: #666; } }
    label { display: flex; gap: 8px; align-items: center; font-size: 14px; cursor: pointer; }
    input { accent-color: #111; }
    textarea { width: 100%; box-sizing: border-box; margin-top: 8px; padding: 8px 10px; border: 1px solid #ccc; border-radius: 8px; font: inherit; }
    .danger { --mat-button-filled-container-color: var(--shop-danger); }
  `,
})
export class CancelOrderDialog {
  protected readonly data = inject<CancelOrderData>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<CancelOrderDialog, { reason: string | null }>);
  protected readonly reasons = REASONS;
  protected readonly reason = signal<string | null>(null);
  protected readonly other = signal('');

  protected confirm(): void {
    const r = this.reason();
    const text = r === 'Other' ? (this.other().trim() || 'Other') : r;
    this.ref.close({ reason: text });
  }
}
