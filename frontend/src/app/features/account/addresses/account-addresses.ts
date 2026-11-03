import { Component, OnInit, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';

import { AccountApi } from '../../../core/api/account.api';
import { Address, landmarkText } from '../../../core/models/account.models';
import { NotifyService } from '../../../core/notify.service';
import { confirmAction } from '../../../shared/confirm-dialog/confirm-dialog';
import { AddressDialog } from './address-dialog';

@Component({
  selector: 'app-account-addresses',
  template: `
    <header class="page-head head-row">
      <div>
        <h1>Addresses</h1>
        <p>Saved delivery addresses. Your default address is pre-selected at checkout.</p>
      </div>
      <button class="btn" type="button" (click)="edit(null)">
        <span class="material-symbols-outlined">add</span>&nbsp;Add address
      </button>
    </header>

    @if (loading()) {
      <p class="muted">Loading…</p>
    } @else if (addresses().length === 0) {
      <div class="panel empty">
        <span class="material-symbols-outlined">home_pin</span>
        <p>You have no saved addresses yet.</p>
        <button class="btn" type="button" (click)="edit(null)">Add your first address</button>
      </div>
    } @else {
      <div class="cards">
        @for (a of addresses(); track a.id) {
          <article class="panel card" [class.default]="a.isDefault">
            <div class="top">
              @if (a.label) { <span class="tag">{{ a.label }}</span> }
              @if (a.isDefault) { <span class="tag dark">Default</span> }
            </div>
            <address>
              <strong>{{ a.fullName }}</strong><br>
              {{ a.line1 }}@if (a.line2) {, {{ a.line2 }}}<br>
              @if (a.landmark) { {{ landmark(a.landmark) }}<br> }
              {{ a.city }}, {{ a.state }} {{ a.pincode }}<br>
              +91 {{ a.phone }}
            </address>
            <div class="row-actions">
              <button type="button" (click)="edit(a)">Edit</button>
              @if (!a.isDefault) { <button type="button" (click)="makeDefault(a)">Set as default</button> }
              <button type="button" class="danger" (click)="remove(a)">Delete</button>
            </div>
          </article>
        }
      </div>
    }
  `,
  styleUrl: '../account-pages.scss',
  styles: `
    .head-row { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; }
    .cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
    .card { display: flex; flex-direction: column; gap: 12px; margin: 0; }
    .card.default { border-color: #111; }
    .top { display: flex; gap: 6px; min-height: 22px; }
    .tag { margin-left: 0; }
    .tag.dark { background: #111; color: #fff; }
    .row-actions { display: flex; gap: 16px; margin-top: auto; padding-top: 8px; border-top: 1px solid var(--shop-line); }
    .row-actions button { border: none; background: none; padding: 6px 0; font: inherit; font-weight: 600; cursor: pointer;
      text-decoration: underline; }
    .row-actions .danger { color: var(--shop-danger); margin-left: auto; }
  `,
})
export class AccountAddresses implements OnInit {
  private readonly api = inject(AccountApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);

  protected readonly addresses = signal<Address[]>([]);
  protected readonly loading = signal(true);
  protected readonly landmark = landmarkText;

  ngOnInit(): void {
    this.load();
  }

  protected edit(address: Address | null): void {
    this.dialog
      .open(AddressDialog, { data: address, width: '640px', maxWidth: '95vw', autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((saved?: Address) => {
        if (saved) {
          this.notify.success(address ? 'Address updated.' : 'Address saved.');
          this.load();
        }
      });
  }

  protected makeDefault(address: Address): void {
    this.api.makeDefault(address.id).subscribe({
      next: () => {
        this.notify.success('Default address updated.');
        this.load();
      },
      error: (err: unknown) => this.notify.error(err),
    });
  }

  protected remove(address: Address): void {
    confirmAction(this.dialog, {
      title: 'Delete this address?',
      message: `${address.line1}, ${address.city} will be removed from your saved addresses.`,
      confirmLabel: 'Delete',
      danger: true,
    }).subscribe((ok) => {
      if (!ok) return;
      this.api.deleteAddress(address.id).subscribe({
        next: () => {
          this.notify.success('Address deleted.');
          this.load();
        },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }

  private load(): void {
    this.api.addresses().subscribe({
      next: (list) => {
        this.addresses.set(list);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Could not load your addresses.');
      },
    });
  }
}
