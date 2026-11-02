import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { apiErrorMessage } from '../../../core/api/api-error';
import { AdminCategory, CategoryRequest } from '../../../core/models/catalog.models';

@Component({
  selector: 'app-category-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatCheckboxModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data ? 'Edit category' : 'New category' }}</h2>
    <form [formGroup]="form" (ngSubmit)="save()" novalidate>
      <mat-dialog-content class="fields">
        <mat-form-field>
          <mat-label>Name</mat-label>
          <input matInput formControlName="name" maxlength="80" required cdkFocusInitial>
          @if (form.controls.name.hasError('required')) { <mat-error>Name is required</mat-error> }
        </mat-form-field>
        <mat-form-field>
          <mat-label>URL name (slug)</mat-label>
          <input matInput formControlName="slug" placeholder="Generated from the name">
          @if (form.controls.slug.hasError('pattern')) { <mat-error>Lowercase letters, digits and dashes only</mat-error> }
        </mat-form-field>
        <mat-form-field>
          <mat-label>Description</mat-label>
          <textarea matInput formControlName="description" rows="3" maxlength="500"></textarea>
        </mat-form-field>
        <mat-form-field>
          <mat-label>Display order</mat-label>
          <input matInput type="number" formControlName="sortOrder" min="0" max="999">
        </mat-form-field>
        <mat-checkbox formControlName="active">Visible in the shop</mat-checkbox>
        @if (data?.system) {
          <p class="muted note">Built-in category: it cannot be deleted and its internal code stays {{ data!.code }}.</p>
        }
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Cancel</button>
        <button mat-flat-button type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save' }}</button>
      </mat-dialog-actions>
    </form>
  `,
  styles: `
    .fields { display: flex; flex-direction: column; gap: 8px; min-width: min(420px, 80vw); padding-top: 8px; }
    .error { color: var(--shop-danger); margin: 0; }
    .note { font-size: 13px; margin: 0; }
  `,
})
export class CategoryDialog {
  protected readonly data = inject<AdminCategory | null>(MAT_DIALOG_DATA);
  private readonly ref = inject(MatDialogRef<CategoryDialog, AdminCategory>);
  private readonly api = inject(AdminCatalogApi);

  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: [this.data?.name ?? '', [Validators.required, Validators.maxLength(80)]],
    slug: [this.data?.slug ?? '', [Validators.maxLength(100), Validators.pattern(/^[a-z0-9]+(-[a-z0-9]+)*$/)]],
    description: [this.data?.description ?? '', Validators.maxLength(500)],
    sortOrder: [this.data?.sortOrder ?? 10, [Validators.min(0), Validators.max(999)]],
    active: [this.data?.active ?? true],
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const body: CategoryRequest = {
      name: v.name.trim(),
      slug: v.slug.trim() || null,
      description: v.description.trim() || null,
      imageUrl: this.data?.imageUrl ?? null,
      sortOrder: Number(v.sortOrder),
      active: v.active,
    };
    this.saving.set(true);
    const call = this.data ? this.api.updateCategory(this.data.id, body) : this.api.createCategory(body);
    call.subscribe({
      next: (saved) => this.ref.close(saved),
      error: (err: unknown) => {
        this.saving.set(false);
        this.error.set(apiErrorMessage(err, 'Could not save the category.'));
      },
    });
  }
}
