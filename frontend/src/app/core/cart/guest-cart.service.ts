import { Injectable, computed, signal } from '@angular/core';

/**
 * One line in the shopping bag. Prices here are for display only: the server
 * recalculates every price at checkout (Phase 11/12) and never trusts these values.
 */
export interface CartLine {
  sku: string;
  productId: number;
  slug: string;
  name: string;
  imageUrl?: string;
  sizeCode: string;
  colorCode: string;
  colorName: string;
  unitPrice: number;
  quantity: number;
  /** Most the customer can add, from current availability and the per-item limit. */
  maxQuantity: number;
}

export const CART_STORAGE_KEY = 'tshirt_shop_bag_v1';
const MAX_LINES = 50;

/**
 * Guest bag kept in this browser (SKUs and quantities; the display fields are only a fallback).
 * The cart page prices it on the server, and it is merged into the account cart on sign-in.
 */
@Injectable({ providedIn: 'root' })
export class GuestCartService {
  private readonly state = signal<CartLine[]>(this.read());

  readonly lines = this.state.asReadonly();
  readonly count = computed(() => this.state().reduce((sum, l) => sum + l.quantity, 0));

  /** Adds a line or increases its quantity, never above maxQuantity. Returns the quantity now in the bag. */
  add(line: CartLine): number {
    const lines = [...this.state()];
    const index = lines.findIndex((l) => l.sku === line.sku);
    let quantity: number;
    if (index >= 0) {
      const existing = lines[index];
      const max = Math.max(1, line.maxQuantity);
      quantity = Math.min(existing.quantity + line.quantity, max);
      lines[index] = { ...existing, ...line, quantity, maxQuantity: max };
    } else {
      if (lines.length >= MAX_LINES) {
        return 0;
      }
      quantity = Math.min(line.quantity, Math.max(1, line.maxQuantity));
      lines.push({ ...line, quantity });
    }
    this.save(lines);
    return quantity;
  }

  quantityOf(sku: string): number {
    return this.state().find((l) => l.sku === sku)?.quantity ?? 0;
  }

  setQuantity(sku: string, quantity: number): void {
    this.save(this.state().map((l) => (l.sku === sku ? { ...l, quantity: Math.max(1, Math.min(quantity, Math.max(1, l.maxQuantity))) } : l)));
  }

  /** Switches a line to another size or colour, combining it with an existing line for that SKU. */
  replace(oldSku: string, line: CartLine): void {
    const old = this.state().find((l) => l.sku === oldSku);
    if (!old) return;
    const rest = this.state().filter((l) => l.sku !== oldSku);
    const same = rest.find((l) => l.sku === line.sku);
    const merged = same
      ? rest.map((l) => (l.sku === line.sku ? { ...l, quantity: Math.min(l.quantity + old.quantity, Math.max(1, line.maxQuantity)) } : l))
      : [...rest, { ...old, ...line, quantity: Math.min(old.quantity, Math.max(1, line.maxQuantity)) }];
    this.save(merged);
  }

  remove(sku: string): void {
    this.save(this.state().filter((l) => l.sku !== sku));
  }

  clear(): void {
    this.save([]);
  }

  private save(lines: CartLine[]): void {
    this.state.set(lines);
    try {
      localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(lines));
    } catch {
      // Storage unavailable (private mode, quota): the bag still works for this visit.
    }
  }

  private read(): CartLine[] {
    try {
      const raw = localStorage.getItem(CART_STORAGE_KEY);
      const parsed: unknown = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed.filter(isCartLine).slice(0, MAX_LINES) : [];
    } catch {
      return [];
    }
  }
}

function isCartLine(value: unknown): value is CartLine {
  const v = value as CartLine;
  return !!v && typeof v.sku === 'string' && typeof v.productId === 'number' && typeof v.quantity === 'number'
    && v.quantity > 0 && typeof v.unitPrice === 'number' && typeof v.slug === 'string';
}
