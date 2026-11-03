import { Params } from '@angular/router';

import { CatalogQuery, CatalogSort } from '../../core/models/store.models';

/** Listing state lives in the URL so links, refresh and the back button all work. */
export interface ListingState {
  q: string | null;
  sizes: string[];
  colors: string[];
  minPrice: number | null;
  maxPrice: number | null;
  inStock: boolean;
  onSale: boolean;
  sort: CatalogSort;
  page: number;
}

export const PAGE_SIZE = 12;

export const SORT_OPTIONS: { value: CatalogSort; label: string }[] = [
  { value: 'NEWEST', label: 'Newest' },
  { value: 'POPULAR', label: 'Best selling' },
  { value: 'PRICE_ASC', label: 'Price: low to high' },
  { value: 'PRICE_DESC', label: 'Price: high to low' },
  { value: 'RATING', label: 'Top rated' },
  { value: 'NAME_ASC', label: 'Name: A to Z' },
];

export const PRICE_PRESETS: { label: string; min: number | null; max: number | null }[] = [
  { label: 'Under ₹500', min: null, max: 499 },
  { label: '₹500 – ₹999', min: 500, max: 999 },
  { label: '₹1,000 – ₹1,499', min: 1000, max: 1499 },
  { label: '₹1,500 and above', min: 1500, max: null },
];

const SORTS = new Set<string>(SORT_OPTIONS.map((o) => o.value));
const CODE = /^[A-Z0-9_]{1,20}$/;

export function parseListingState(params: Params): ListingState {
  const sort = String(params['sort'] ?? '').toUpperCase();
  return {
    q: typeof params['q'] === 'string' && params['q'].trim() ? params['q'].trim().slice(0, 100) : null,
    sizes: codes(params['sizes']),
    colors: codes(params['colors']),
    minPrice: price(params['minPrice']),
    maxPrice: price(params['maxPrice']),
    inStock: params['inStock'] === 'true',
    onSale: params['onSale'] === 'true',
    sort: (SORTS.has(sort) ? sort : 'NEWEST') as CatalogSort,
    page: Math.max(0, Math.min(10000, Number.parseInt(params['page'] ?? '1', 10) - 1 || 0)),
  };
}

/** Query parameters for the URL; defaults are left out to keep links short. Page is 1-based. */
export function toQueryParams(state: ListingState): Params {
  return {
    q: state.q || null,
    sizes: state.sizes.length ? state.sizes.join(',') : null,
    colors: state.colors.length ? state.colors.join(',') : null,
    minPrice: state.minPrice ?? null,
    maxPrice: state.maxPrice ?? null,
    inStock: state.inStock || null,
    onSale: state.onSale || null,
    sort: state.sort === 'NEWEST' ? null : state.sort,
    page: state.page > 0 ? state.page + 1 : null,
  };
}

export function toCatalogQuery(state: ListingState, category: string | null): CatalogQuery {
  return {
    category,
    q: state.q,
    sizes: state.sizes,
    colors: state.colors,
    minPrice: state.minPrice,
    maxPrice: state.maxPrice,
    inStock: state.inStock,
    onSale: state.onSale,
    sort: state.sort,
    page: state.page,
    size: PAGE_SIZE,
  };
}

export function toggle(list: string[], code: string): string[] {
  return list.includes(code) ? list.filter((c) => c !== code) : [...list, code];
}

export function activeFilterCount(state: ListingState): number {
  return state.sizes.length + state.colors.length + (state.minPrice != null || state.maxPrice != null ? 1 : 0)
    + (state.inStock ? 1 : 0) + (state.onSale ? 1 : 0);
}

/** Page numbers to show, with null for a gap, e.g. [0, null, 4, 5, 6, null, 11]. */
export function pageWindow(current: number, total: number): (number | null)[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i);
  }
  const pages = new Set([0, total - 1, current - 1, current, current + 1].filter((p) => p >= 0 && p < total));
  const sorted = [...pages].sort((a, b) => a - b);
  const out: (number | null)[] = [];
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push(null);
    out.push(p);
  });
  return out;
}

function codes(value: unknown): string[] {
  if (typeof value !== 'string') return [];
  return [...new Set(value.split(',').map((s) => s.trim().toUpperCase()).filter((s) => CODE.test(s)))].slice(0, 20);
}

function price(value: unknown): number | null {
  if (value === undefined || value === null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) && n >= 0 && n <= 1_000_000 ? Math.round(n) : null;
}
