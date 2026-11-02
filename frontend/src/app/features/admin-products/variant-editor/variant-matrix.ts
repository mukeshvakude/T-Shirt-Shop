import { AdminVariant, Color, Size, SizeGroup, VariantRequest } from '../../../core/models/catalog.models';

/** One cell of the size x colour grid. quantity null means "not offered". */
export interface MatrixCell {
  sizeId: number;
  colorId: number;
  quantity: number | null;
  reserved: number;
  threshold: number;
  sku?: string;
}

export const DEFAULT_LOW_STOCK_THRESHOLD = 5;

export function cellKey(sizeId: number, colorId: number): string {
  return `${sizeId}:${colorId}`;
}

/** Pants use waist sizes; everything else uses apparel sizes (mirrors the backend rule). */
export function sizeGroupForCategory(categoryCode: string | null | undefined): SizeGroup {
  return categoryCode === 'PANTS' ? 'WAIST' : 'APPAREL';
}

/** Colours already used by active variants, in display order. */
export function activeColorIds(variants: AdminVariant[], colors: Color[]): number[] {
  const used = new Set(variants.filter((v) => v.active).map((v) => v.colorId));
  return colors.filter((c) => used.has(c.id)).map((c) => c.id);
}

/** Builds the grid for the given sizes and colours from the product's current variants. */
export function buildMatrix(sizes: Size[], colorIds: number[], variants: AdminVariant[]): Map<string, MatrixCell> {
  const byKey = new Map(variants.map((v) => [cellKey(v.sizeId, v.colorId), v]));
  const cells = new Map<string, MatrixCell>();
  for (const size of sizes) {
    for (const colorId of colorIds) {
      const existing = byKey.get(cellKey(size.id, colorId));
      cells.set(cellKey(size.id, colorId), {
        sizeId: size.id,
        colorId,
        quantity: existing?.active ? existing.quantityOnHand : null,
        reserved: existing?.quantityReserved ?? 0,
        threshold: existing?.lowStockThreshold ?? DEFAULT_LOW_STOCK_THRESHOLD,
        sku: existing?.sku,
      });
    }
  }
  return cells;
}

/** Parses a quantity input: blank means "not offered", otherwise a whole number >= 0. */
export function parseQuantity(raw: string): number | null | 'invalid' {
  const value = raw.trim();
  if (value === '') {
    return null;
  }
  if (!/^\d{1,6}$/.test(value)) {
    return 'invalid';
  }
  return Number(value);
}

/** Problems that would make the backend reject the save, as messages for the user. */
export function validateCells(cells: Iterable<MatrixCell>, sizes: Size[], colors: Color[]): string[] {
  const errors: string[] = [];
  const sizeCode = new Map(sizes.map((s) => [s.id, s.code]));
  const colorName = new Map(colors.map((c) => [c.id, c.name]));
  for (const cell of cells) {
    if (cell.quantity !== null && cell.quantity < cell.reserved) {
      errors.push(`${sizeCode.get(cell.sizeId)} / ${colorName.get(cell.colorId)}: at least ${cell.reserved} are reserved by pending orders.`);
    }
  }
  return errors;
}

/** Offered cells become active variants; everything else is left out (the backend deactivates it). */
export function toVariantRequests(cells: Iterable<MatrixCell>): VariantRequest[] {
  const requests: VariantRequest[] = [];
  for (const cell of cells) {
    if (cell.quantity !== null) {
      requests.push({
        sizeId: cell.sizeId,
        colorId: cell.colorId,
        quantityOnHand: cell.quantity,
        lowStockThreshold: cell.threshold,
        active: true,
      });
    }
  }
  return requests;
}
