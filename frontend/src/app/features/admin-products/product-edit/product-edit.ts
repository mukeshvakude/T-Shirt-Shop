import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { apiErrorCode } from '../../../core/api/api-error';
import { ReferenceApi } from '../../../core/api/reference.api';
import { AdminCategory, AdminProduct, ProductRequest, ProductStatus } from '../../../core/models/catalog.models';
import { NotifyService } from '../../../core/notify.service';
import { confirmAction } from '../../../shared/confirm-dialog/confirm-dialog';
import { ImageManager } from '../image-manager/image-manager';
import { VariantEditor } from '../variant-editor/variant-editor';

const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const CUSTOM_CATEGORY = 'CUSTOM_TSHIRTS';

/** Discount must be lower than the base price (mirrors the backend rule). */
export function discountBelowBase(group: AbstractControl): ValidationErrors | null {
  const base = Number(group.get('basePrice')?.value);
  const raw = group.get('discountPrice')?.value;
  if (raw === null || raw === '' || raw === undefined) {
    return null;
  }
  return Number(raw) < base ? null : { discountNotLower: true };
}

@Component({
  selector: 'app-product-edit',
  imports: [
    ReactiveFormsModule, RouterLink, MatFormFieldModule, MatInputModule, MatSelectModule, MatCheckboxModule,
    MatButtonModule, MatProgressBarModule, VariantEditor, ImageManager,
  ],
  templateUrl: './product-edit.html',
  styleUrl: './product-edit.scss',
})
export class ProductEdit implements OnInit {
  private readonly api = inject(AdminCatalogApi);
  private readonly reference = inject(ReferenceApi);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Route parameter; absent on /admin/products/new. */
  readonly id = input<string>();

  protected readonly product = signal<AdminProduct | null>(null);
  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly sizes = toSignal(this.reference.sizes(), { initialValue: [] });
  protected readonly colors = toSignal(this.reference.colors(), { initialValue: [] });
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly isNew = computed(() => !this.id() || this.id() === 'new');
  protected readonly hasActiveVariant = computed(() => this.product()?.variants.some((v) => v.active) ?? false);

  protected readonly form = this.fb.group(
    {
      categoryId: this.fb.control<number | null>(null, Validators.required),
      name: ['', [Validators.required, Validators.maxLength(150)]],
      slug: ['', [Validators.maxLength(170), Validators.pattern(SLUG_PATTERN)]],
      shortDescription: ['', Validators.maxLength(300)],
      description: ['', Validators.maxLength(10000)],
      basePrice: this.fb.control<number | null>(null, [Validators.required, Validators.min(1), Validators.max(999999.99)]),
      discountPrice: this.fb.control<number | null>(null, [Validators.min(1)]),
      status: this.fb.control<ProductStatus>('DRAFT'),
      featured: false,
      newArrival: false,
      material: ['', Validators.maxLength(100)],
      fit: ['', Validators.maxLength(50)],
      careInstructions: ['', Validators.maxLength(300)],
    },
    { validators: discountBelowBase },
  );

  ngOnInit(): void {
    this.api.listCategories().subscribe({
      next: (categories) => this.categories.set(categories),
      error: (err: unknown) => this.notify.error(err),
    });
    if (this.isNew()) {
      this.loading.set(false);
      return;
    }
    this.load();
  }

  protected load(): void {
    const id = Number(this.id());
    this.loading.set(true);
    this.api.getProduct(id).subscribe({
      next: (product) => {
        this.product.set(product);
        this.form.reset({
          categoryId: product.categoryId,
          name: product.name,
          slug: product.slug,
          shortDescription: product.shortDescription ?? '',
          description: product.description ?? '',
          basePrice: product.basePrice,
          discountPrice: product.discountPrice ?? null,
          status: product.status,
          featured: product.featured,
          newArrival: product.newArrival,
          material: product.material ?? '',
          fit: product.fit ?? '',
          careInstructions: product.careInstructions ?? '',
        });
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Product not found.');
        void this.router.navigate(['/admin/products']);
      },
    });
  }

  protected statusOptions(): ProductStatus[] {
    return this.isNew() ? ['DRAFT', 'INACTIVE'] : ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'];
  }

  protected save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }
    const request = this.toRequest();
    this.saving.set(true);
    const call = this.isNew() ? this.api.createProduct(request) : this.api.updateProduct(this.product()!.id, request);
    call.subscribe({
      next: (product) => {
        this.saving.set(false);
        this.form.markAsPristine();
        if (this.isNew()) {
          this.notify.success('Product created. Now add sizes, stock and images.');
          void this.router.navigate(['/admin/products', product.id], { replaceUrl: true });
        } else {
          this.notify.success('Product saved.');
          this.product.set(product);
        }
      },
      error: (err: unknown) => {
        this.saving.set(false);
        if (apiErrorCode(err) === 'SLUG_TAKEN') {
          this.form.controls.slug.setErrors({ taken: true });
        }
        this.notify.error(err, 'Could not save the product.');
      },
    });
  }

  protected setStatus(status: ProductStatus): void {
    const product = this.product();
    if (!product) {
      return;
    }
    this.api.changeStatus(product.id, status).subscribe({
      next: (updated) => {
        this.product.set(updated);
        this.form.controls.status.setValue(updated.status);
        this.notify.success(status === 'ACTIVE' ? 'Product is now live in the shop.' : `Status changed to ${status}.`);
      },
      error: (err: unknown) => this.notify.error(err),
    });
  }

  protected deleteProduct(): void {
    const product = this.product();
    if (!product) {
      return;
    }
    confirmAction(this.dialog, {
      title: `Delete “${product.name}”?`,
      message: 'This permanently removes the product, its stock records and images. '
        + 'Products that appear in orders cannot be deleted; archive them instead.',
      confirmLabel: 'Delete product',
      danger: true,
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deleteProduct(product.id).subscribe({
        next: () => {
          this.notify.success('Product deleted.');
          void this.router.navigate(['/admin/products']);
        },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }

  protected reloadAfterChildChange(): void {
    const product = this.product();
    if (product) {
      this.api.getProduct(product.id).subscribe({
        next: (fresh) => this.product.set(fresh),
        error: (err: unknown) => this.notify.error(err),
      });
    }
  }

  private toRequest(): ProductRequest {
    const v = this.form.getRawValue();
    const category = this.categories().find((c) => c.id === v.categoryId);
    const trim = (s: string) => (s.trim() === '' ? null : s.trim());
    return {
      categoryId: v.categoryId!,
      name: v.name.trim(),
      slug: trim(v.slug),
      shortDescription: trim(v.shortDescription),
      description: trim(v.description),
      basePrice: Number(v.basePrice),
      discountPrice: v.discountPrice === null || (v.discountPrice as unknown) === '' ? null : Number(v.discountPrice),
      status: v.status,
      featured: v.featured,
      newArrival: v.newArrival,
      customizable: category?.code === CUSTOM_CATEGORY,
      material: trim(v.material),
      fit: trim(v.fit),
      careInstructions: trim(v.careInstructions),
    };
  }
}
