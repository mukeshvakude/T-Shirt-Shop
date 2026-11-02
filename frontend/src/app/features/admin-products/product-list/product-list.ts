import { CurrencyPipe, DatePipe, TitleCasePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import {
  AdminCategory,
  AdminProductListItem,
  AdminProductSort,
  PRODUCT_STATUSES,
  ProductStatus,
} from '../../../core/models/catalog.models';
import { NotifyService } from '../../../core/notify.service';
import { ImgFallback } from '../../../shared/img-fallback.directive';

@Component({
  selector: 'app-product-list',
  imports: [
    ReactiveFormsModule, RouterLink, CurrencyPipe, DatePipe, TitleCasePipe, MatTableModule, MatPaginatorModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatProgressBarModule, ImgFallback,
  ],
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList implements OnInit {
  private readonly api = inject(AdminCatalogApi);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly columns = ['image', 'name', 'category', 'price', 'stock', 'status', 'updated'];
  protected readonly statuses = PRODUCT_STATUSES;
  protected readonly sorts: { value: AdminProductSort; label: string }[] = [
    { value: 'UPDATED_DESC', label: 'Recently updated' },
    { value: 'NEWEST', label: 'Newest' },
    { value: 'NAME_ASC', label: 'Name A–Z' },
    { value: 'PRICE_ASC', label: 'Price: low to high' },
    { value: 'PRICE_DESC', label: 'Price: high to low' },
    { value: 'POPULAR', label: 'Best selling' },
  ];

  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly categoryId = signal<number | null>(null);
  protected readonly status = signal<ProductStatus | null>(null);
  protected readonly sort = signal<AdminProductSort>('UPDATED_DESC');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(20);

  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly rows = signal<AdminProductListItem[]>([]);
  protected readonly total = signal(0);
  protected readonly loading = signal(false);

  ngOnInit(): void {
    this.api.listCategories().subscribe({ next: (c) => this.categories.set(c), error: (e) => this.notify.error(e) });
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload(true));
    this.reload();
  }

  protected setCategory(id: number | null): void {
    this.categoryId.set(id);
    this.reload(true);
  }

  protected setStatus(status: ProductStatus | null): void {
    this.status.set(status);
    this.reload(true);
  }

  protected setSort(sort: AdminProductSort): void {
    this.sort.set(sort);
    this.reload(true);
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.reload();
  }

  protected open(row: AdminProductListItem): void {
    void this.router.navigate(['/admin/products', row.id]);
  }

  protected reload(resetPage = false): void {
    if (resetPage) {
      this.pageIndex.set(0);
    }
    this.loading.set(true);
    this.api
      .listProducts({
        q: this.search.value,
        categoryId: this.categoryId(),
        status: this.status(),
        sort: this.sort(),
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => {
          this.rows.set(page.content);
          this.total.set(page.totalElements);
          this.loading.set(false);
        },
        error: (err: unknown) => {
          this.loading.set(false);
          this.notify.error(err, 'Could not load products.');
        },
      });
  }
}
