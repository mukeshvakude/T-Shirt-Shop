import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { AdminCatalogApi } from '../../../core/api/admin-catalog.api';
import { AdminProduct, AdminVariant, Color, Size } from '../../../core/models/catalog.models';
import { NotifyService } from '../../../core/notify.service';
import {
  MatrixCell,
  activeColorIds,
  buildMatrix,
  cellKey,
  parseQuantity,
  sizeGroupForCategory,
  toVariantRequests,
  validateCells,
} from './variant-matrix';

/**
 * Size x colour grid. Enter a stock quantity to offer a combination; leave it blank to
 * stop offering it. Saving replaces the product's variant list in one request.
 */
@Component({
  selector: 'app-variant-editor',
  imports: [MatFormFieldModule, MatSelectModule, MatInputModule, MatButtonModule],
  templateUrl: './variant-editor.html',
  styleUrl: './variant-editor.scss',
})
export class VariantEditor {
  private readonly api = inject(AdminCatalogApi);
  private readonly notify = inject(NotifyService);

  readonly product = input.required<AdminProduct>();
  readonly sizes = input.required<Size[]>();
  readonly colors = input.required<Color[]>();
  readonly saved = output<AdminVariant[]>();

  protected readonly sizeRows = computed(() => {
    const group = sizeGroupForCategory(this.product().categoryCode);
    return this.sizes().filter((s) => s.sizeGroup === group);
  });
  protected readonly selectedColorIds = signal<number[]>([]);
  protected readonly selectedColors = computed(() => {
    const ids = new Set(this.selectedColorIds());
    return this.colors().filter((c) => ids.has(c.id));
  });
  protected readonly cells = signal(new Map<string, MatrixCell>());
  protected readonly invalid = signal(new Set<string>());
  protected readonly dirty = signal(false);
  protected readonly saving = signal(false);
  protected readonly totalUnits = computed(() =>
    [...this.cells().values()].reduce((sum, c) => sum + (c.quantity ?? 0), 0),
  );

  constructor() {
    // Rebuild the grid whenever the product (re)loads.
    effect(() => {
      const product = this.product();
      const colors = this.colors();
      const ids = activeColorIds(product.variants, colors);
      this.selectedColorIds.set(ids);
      this.cells.set(buildMatrix(this.sizeRows(), ids, product.variants));
      this.invalid.set(new Set());
      this.dirty.set(false);
    });
  }

  protected key = cellKey;

  protected cell(sizeId: number, colorId: number): MatrixCell | undefined {
    return this.cells().get(cellKey(sizeId, colorId));
  }

  protected onColorsChange(ids: number[]): void {
    const ordered = this.colors().filter((c) => ids.includes(c.id)).map((c) => c.id);
    const current = this.cells();
    const next = buildMatrix(this.sizeRows(), ordered, this.product().variants);
    for (const [key, cell] of next) {
      const previous = current.get(key);
      if (previous) {
        next.set(key, previous);
      }
    }
    this.selectedColorIds.set(ordered);
    this.cells.set(next);
    this.dirty.set(true);
  }

  protected onQuantity(sizeId: number, colorId: number, raw: string): void {
    const key = cellKey(sizeId, colorId);
    const parsed = parseQuantity(raw);
    const invalid = new Set(this.invalid());
    if (parsed === 'invalid') {
      invalid.add(key);
      this.invalid.set(invalid);
      return;
    }
    invalid.delete(key);
    this.invalid.set(invalid);
    const cells = new Map(this.cells());
    const cell = cells.get(key);
    if (cell) {
      cells.set(key, { ...cell, quantity: parsed });
      this.cells.set(cells);
      this.dirty.set(true);
    }
  }

  protected fillColumn(colorId: number, raw: string): void {
    const parsed = parseQuantity(raw);
    if (parsed === 'invalid' || parsed === null) {
      return;
    }
    const cells = new Map(this.cells());
    for (const size of this.sizeRows()) {
      const key = cellKey(size.id, colorId);
      const cell = cells.get(key);
      if (cell) {
        cells.set(key, { ...cell, quantity: Math.max(parsed, cell.reserved) });
      }
    }
    this.cells.set(cells);
    this.dirty.set(true);
  }

  protected save(): void {
    if (this.invalid().size > 0) {
      this.notify.error('Quantities must be whole numbers (or blank to stop offering that size).');
      return;
    }
    const cells = [...this.cells().values()];
    const problems = validateCells(cells, this.sizeRows(), this.colors());
    if (problems.length) {
      this.notify.error(problems.join(' '));
      return;
    }
    this.saving.set(true);
    this.api.replaceVariants(this.product().id, toVariantRequests(cells)).subscribe({
      next: (variants) => {
        this.saving.set(false);
        this.dirty.set(false);
        this.notify.success('Sizes, colours and stock saved.');
        this.saved.emit(variants);
      },
      error: (err: unknown) => {
        this.saving.set(false);
        this.notify.error(err, 'Could not save stock.');
      },
    });
  }
}
