import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { AdminVariant } from '../../../core/models/catalog.models';
import { NotifyService } from '../../../core/notify.service';

/** SKUs at or below their low-stock threshold, with quick restocking. */
@Component({
  selector: 'app-low-stock',
  imports: [MatTableModule, MatPaginatorModule, MatButtonModule, MatProgressBarModule, RouterLink],
  templateUrl: './low-stock.html',
  styles: `
    .table-card { padding: 0; overflow: hidden; }
    .table-scroll { overflow-x: auto; }
    table { width: 100%; }
    .swatch { display: inline-block; width: 12px; height: 12px; border-radius: 50%; border: 1px solid #ccc;
      margin-right: 6px; vertical-align: middle; }
    .zero { color: var(--shop-danger); font-weight: 600; }
    .restock { display: flex; gap: 6px; align-items: center; }
    .restock input { width: 72px; height: 34px; padding: 0 8px; border: 1px solid #d6d6d6; border-radius: 6px;
      font: inherit; }
    a { font-weight: 600; text-decoration: none; }
    a:hover { text-decoration: underline; }
  `,
})
export class LowStock implements OnInit {
  private readonly api = inject(AdminCatalogApi);
  private readonly notify = inject(NotifyService);

  protected readonly columns = ['product', 'variant', 'available', 'reserved', 'threshold', 'restock'];
  protected readonly rows = signal<AdminVariant[]>([]);
  protected readonly total = signal(0);
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(50);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.lowStock(this.pageIndex(), this.pageSize()).subscribe({
      next: (page) => {
        this.rows.set(page.content);
        this.total.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Could not load stock.');
      },
    });
  }

  protected restock(variant: AdminVariant, raw: string): void {
    const amount = Number(raw);
    if (!Number.isInteger(amount) || amount <= 0 || amount > 100000) {
      this.notify.error('Enter a whole number of units to add.');
      return;
    }
    this.api.updateStock(variant.id, { adjustBy: amount }).subscribe({
      next: (updated) => {
        this.notify.success(`${variant.sku}: ${updated.available} now available.`);
        this.load();
      },
      error: (err: unknown) => this.notify.error(err),
    });
  }
}
