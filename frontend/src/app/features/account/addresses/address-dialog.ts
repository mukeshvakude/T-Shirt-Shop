import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AccountApi } from '../../../core/api/account.api';
import { apiErrorMessage } from '../../../core/api/api-error';
import { AuthService } from '../../../core/auth/auth.service';
import { Address, AddressRequest } from '../../../core/models/account.models';

export const ADDRESS_LABELS = ['Home', 'Work', 'Other'];

@Component({
  selector: 'app-address-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatCheckboxModule, MatButtonModule],
  templateUrl: './address-dialog.html',
  styles: `
    form { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 14px; padding-top: 6px; }
    mat-form-field { width: 100%; }
    .full { grid-column: 1 / -1; }
    .labels { display: flex; gap: 8px; margin-bottom: 8px; }
    .labels button { height: 34px; padding: 0 14px; border-radius: 999px; border: 1px solid var(--shop-line);
      background: #fff; font: inherit; cursor: pointer; }
    .labels button.on { background: #111; color: #fff; border-color: #111; }
    .error { color: var(--shop-danger); margin: 0; }
    @media (max-width: 600px) { form { grid-template-columns: 1fr; } }
  `,
})
export class AddressDialog {
  protected readonly data = inject<Address | null>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<AddressDialog, Address>);
  private readonly api = inject(AccountApi);
  private readonly user = inject(AuthService).user();

  protected readonly labels = ADDRESS_LABELS;
  protected readonly states = toSignal(this.api.states(), { initialValue: [] as string[] });
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    label: [this.data?.label ?? 'Home', Validators.maxLength(30)],
    fullName: [this.data?.fullName ?? this.user?.fullName ?? '', [Validators.required, Validators.minLength(2), Validators.maxLength(120)]],
    phone: [this.data?.phone ?? this.user?.phone ?? '', [Validators.required, Validators.pattern(/^[6-9]\d{9}$/)]],
    line1: [this.data?.line1 ?? '', [Validators.required, Validators.maxLength(200)]],
    line2: [this.data?.line2 ?? '', Validators.maxLength(200)],
    landmark: [this.data?.landmark ?? '', Validators.maxLength(120)],
    city: [this.data?.city ?? '', [Validators.required, Validators.maxLength(80)]],
    state: [this.data?.state ?? '', Validators.required],
    pincode: [this.data?.pincode ?? '', [Validators.required, Validators.pattern(/^[1-9]\d{5}$/)]],
    makeDefault: [this.data?.isDefault ?? false],
  });

  constructor() {
    // The current default stays default; another address must be chosen to change it.
    if (this.data?.isDefault) {
      this.form.controls.makeDefault.disable();
    }
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: AddressRequest = {
      label: v.label.trim() || null,
      fullName: v.fullName.trim(),
      phone: v.phone.trim(),
      line1: v.line1.trim(),
      line2: v.line2.trim() || null,
      landmark: v.landmark.trim() || null,
      city: v.city.trim(),
      state: v.state,
      pincode: v.pincode.trim(),
      makeDefault: v.makeDefault,
    };
    this.saving.set(true);
    this.error.set(null);
    const call = this.data ? this.api.updateAddress(this.data.id, body) : this.api.createAddress(body);
    call.subscribe({
      next: (saved) => this.ref.close(saved),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Could not save the address.'));
      },
    });
  }
}
