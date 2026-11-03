import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, distinctUntilChanged, map, of, switchMap, tap } from 'rxjs';

import { CatalogApi } from '../../../core/api/catalog.api';
import { SITE } from '../../../core/config/site.config';
import { Category, Page } from '../../../core/models/catalog.models';
import { CatalogSort, Facets, ProductSummary } from '../../../core/models/store.models';
import { ProductCard } from '../../../shared/product-card/product-card';
import {
  ListingState,
  PAGE_SIZE,
  PRICE_PRESETS,
  SORT_OPTIONS,
  activeFilterCount,
  pageWindow,
  parseListingState,
  toCatalogQuery,
  toQueryParams,
  toggle,
} from '../listing-query';

type LoadState = 'loading' | 'ready' | 'error';

/** One page for /shop, /shop/:category and /search. */
@Component({
  selector: 'app-product-listing',
  imports: [RouterLink, CurrencyPipe, ProductCard],
  templateUrl: './product-listing.html',
  styleUrl: './product-listing.scss',
})
export class ProductListing {
  private readonly api = inject(CatalogApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly title = inject(Title);

  protected readonly sortOptions = SORT_OPTIONS;
  protected readonly pricePresets = PRICE_PRESETS;
  protected readonly filtersOpen = signal(false);

  private readonly categorySlug = toSignal(
    this.route.paramMap.pipe(map((p) => p.get('category'))), { initialValue: null as string | null });
  protected readonly state = toSignal(
    this.route.queryParams.pipe(map(parseListingState)), { requireSync: true });
  protected readonly isSearch = computed(() => this.router.url.startsWith('/search'));

  protected readonly category = signal<Category | null>(null);
  protected readonly facets = signal<Facets | null>(null);
  protected readonly result = signal<Page<ProductSummary> | null>(null);
  protected readonly status = signal<LoadState>('loading');
  /** True when the URL names a category that does not exist (or is hidden). */
  protected readonly categoryMissing = signal(false);

  protected readonly heading = computed(() => {
    if (this.isSearch()) {
      return this.state().q ? `Results for “${this.state().q}”` : 'Search';
    }
    return this.category()?.name ?? (this.categorySlug() ? '' : 'All products');
  });
  protected readonly filterCount = computed(() => activeFilterCount(this.state()));
  protected readonly pages = computed(() => pageWindow(this.state().page, this.result()?.totalPages ?? 0));
  protected readonly rangeLabel = computed(() => {
    const r = this.result();
    if (!r || r.totalElements === 0) return '';
    const from = r.page * PAGE_SIZE + 1;
    return `${from}–${from + r.content.length - 1} of ${r.totalElements}`;
  });
  /** Category facet links: on /shop and on search only. */
  protected readonly showCategoryFacet = computed(() => !this.categorySlug());

  constructor() {
    const slug$ = this.route.paramMap.pipe(map((p) => p.get('category')), distinctUntilChanged());

    // Category name and description.
    slug$
      .pipe(
        switchMap((slug) =>
          (slug ? this.api.category(slug).pipe(catchError(() => of(null))) : of(null)).pipe(
            map((category) => ({ slug, category })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ slug, category }) => {
        this.category.set(category);
        this.categoryMissing.set(!!slug && !category);
        this.updateTitle();
      });

    // Filter options depend only on the category and the search text.
    combineLatest([slug$, this.route.queryParams.pipe(map((p) => parseListingState(p).q), distinctUntilChanged())])
      .pipe(
        switchMap(([slug, q]) => this.api.facets(slug, q).pipe(catchError(() => of(null)))),
        takeUntilDestroyed(),
      )
      .subscribe((facets) => this.facets.set(facets));

    // Results follow every change of the URL.
    combineLatest([slug$, this.route.queryParams.pipe(map(parseListingState))])
      .pipe(
        tap(() => this.status.set('loading')),
        switchMap(([slug, state]) =>
          this.api.search(toCatalogQuery(state, slug)).pipe(
            map((page) => ({ page, ok: true })),
            catchError(() => of({ page: null, ok: false })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ page, ok }) => {
        this.result.set(page);
        this.status.set(ok ? 'ready' : 'error');
        this.updateTitle();
      });
  }

  // ------------------------------------------------------------------ filter actions

  protected toggleSize(code: string): void {
    this.update({ sizes: toggle(this.state().sizes, code) });
  }

  protected toggleColor(code: string): void {
    this.update({ colors: toggle(this.state().colors, code) });
  }

  protected setPrice(min: number | null, max: number | null): void {
    const same = this.state().minPrice === min && this.state().maxPrice === max;
    this.update(same ? { minPrice: null, maxPrice: null } : { minPrice: min, maxPrice: max });
  }

  protected applyCustomPrice(minRaw: string, maxRaw: string): void {
    const min = minRaw.trim() === '' ? null : Math.max(0, Math.round(Number(minRaw)));
    const max = maxRaw.trim() === '' ? null : Math.max(0, Math.round(Number(maxRaw)));
    if ((min !== null && Number.isNaN(min)) || (max !== null && Number.isNaN(max))) return;
    if (min !== null && max !== null && min > max) {
      this.update({ minPrice: max, maxPrice: min });
    } else {
      this.update({ minPrice: min, maxPrice: max });
    }
  }

  protected setFlag(flag: 'inStock' | 'onSale', value: boolean): void {
    this.update({ [flag]: value } as Partial<ListingState>);
  }

  protected setSort(sort: string): void {
    this.update({ sort: sort as CatalogSort });
  }

  protected clearFilters(): void {
    this.update({ sizes: [], colors: [], minPrice: null, maxPrice: null, inStock: false, onSale: false });
  }

  protected goToPage(page: number): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toQueryParams({ ...this.state(), page }),
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  protected sizeName(code: string): string {
    return this.facets()?.sizes.find((s) => s.code === code)?.label ?? code;
  }

  protected colorName(code: string): string {
    return this.facets()?.colors.find((c) => c.code === code)?.name ?? code;
  }

  /** Any filter change returns to the first page. */
  private update(change: Partial<ListingState>): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: toQueryParams({ ...this.state(), ...change, page: 0 }),
    });
  }

  private updateTitle(): void {
    const heading = this.heading();
    this.title.setTitle(heading ? `${heading} | ${SITE.brandName}` : SITE.brandName);
  }
}
