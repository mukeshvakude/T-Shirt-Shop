import { Component, OnInit, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { AdminCategory } from '../../../core/models/catalog.models';
import { NotifyService } from '../../../core/notify.service';
import { confirmAction } from '../../../shared/confirm-dialog/confirm-dialog';
import { CategoryDialog } from '../category-dialog/category-dialog';

@Component({
  selector: 'app-category-list',
  imports: [MatTableModule, MatButtonModule, MatProgressBarModule],
  templateUrl: './category-list.html',
  styles: `
    .table-card { padding: 0; overflow: hidden; }
    table { width: 100%; }
    .name { font-weight: 600; color: var(--shop-black); }
    .badge { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: #f1f1f1; margin-left: 6px; }
    .off { color: var(--shop-muted); }
    .actions { text-align: right; white-space: nowrap; }
  `,
})
export class CategoryList implements OnInit {
  private readonly api = inject(AdminCatalogApi);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);

  protected readonly columns = ['name', 'slug', 'products', 'status', 'actions'];
  protected readonly categories = signal<AdminCategory[]>([]);
  protected readonly loading = signal(true);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.api.listCategories().subscribe({
      next: (categories) => {
        this.categories.set(categories);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        this.notify.error(err, 'Could not load categories.');
      },
    });
  }

  protected edit(category: AdminCategory | null): void {
    this.dialog
      .open(CategoryDialog, { data: category, autoFocus: 'first-tabbable' })
      .afterClosed()
      .subscribe((saved?: AdminCategory) => {
        if (saved) {
          this.notify.success(category ? 'Category saved.' : 'Category created.');
          this.load();
        }
      });
  }

  protected remove(category: AdminCategory): void {
    confirmAction(this.dialog, {
      title: `Delete “${category.name}”?`,
      message: 'Only empty categories can be deleted.',
      confirmLabel: 'Delete',
      danger: true,
    }).subscribe((confirmed) => {
      if (!confirmed) {
        return;
      }
      this.api.deleteCategory(category.id).subscribe({
        next: () => {
          this.notify.success('Category deleted.');
          this.load();
        },
        error: (err: unknown) => this.notify.error(err),
      });
    });
  }
}
