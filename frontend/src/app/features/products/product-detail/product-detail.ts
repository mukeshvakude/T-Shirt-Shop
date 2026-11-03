import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Meta, Title } from '@angular/platform-browser';
import { Router, RouterLink } from '@angular/router';
import { catchError, of, switchMap, tap } from 'rxjs';

import { CatalogApi } from '../../../core/api/catalog.api';
import { GuestCartService } from '../../../core/cart/guest-cart.service';
import { SITE } from '../../../core/config/site.config';
import { ProductDetail as Product, ProductSummary, ReviewHighlight } from '../../../core/models/store.models';
import { WishlistService } from '../../../core/wishlist.service';
import { ImgFallback } from '../../../shared/img-fallback.directive';
import { ProductCard } from '../../../shared/product-card/product-card';
import { RatingStars } from '../../../shared/rating-stars/rating-stars';
import { SizeGuide } from '../../../shared/size-guide/size-guide';
import {
  autoSize,
  colorAvailable,
  defaultColor,
  findVariant,
  galleryFor,
  sizeAvailable,
  stockMessage,
} from './product-options';

type Status = 'loading' | 'ready' | 'not-found' | 'error';

@Component({
  selector: 'app-product-detail',
  imports: [RouterLink, CurrencyPipe, DatePipe, ImgFallback, ProductCard, RatingStars],
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail {
  private readonly api = inject(CatalogApi);
  private readonly cart = inject(GuestCartService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);
  protected readonly wishlist = inject(WishlistService);

  /** Route parameter. */
  readonly slug = input.required<string>();

  protected readonly product = signal<Product | null>(null);
  protected readonly status = signal<Status>('loading');
  protected readonly color = signal<string | null>(null);
  protected readonly size = signal<string | null>(null);
  protected readonly quantity = signal(1);
  protected readonly imageIndex = signal(0);
  protected readonly sizeError = signal(false);
  protected readonly related = signal<ProductSummary[]>([]);
  protected readonly reviews = signal<ReviewHighlight[]>([]);
  protected readonly reviewTotal = signal(0);
  private reviewPage = 0;

  protected readonly gallery = computed(() => galleryFor(this.product()?.images ?? [], this.color()));
  protected readonly mainImage = computed(() => this.gallery()[Math.min(this.imageIndex(), this.gallery().length - 1)]);
  protected readonly colorName = computed(() => this.product()?.colors.find((c) => c.code === this.color())?.name ?? '');
  protected readonly variant = computed(() => {
    const p = this.product();
    return p ? findVariant(p, this.size(), this.color()) : null;
  });
  protected readonly maxQuantity = computed(() => this.variant()?.availableQuantity ?? 0);
  protected readonly stock = computed(() => {
    const p = this.product();
    return p ? stockMessage(p, this.size(), this.color()) : null;
  });
  protected readonly canBuy = computed(() => (this.variant()?.inStock ?? false) && this.quantity() <= this.maxQuantity());
  protected readonly saved = computed(() => {
    const p = this.product();
    return p ? this.wishlist.has(p.id) : false;
  });
  protected readonly sizeGroup = computed(() => this.product()?.sizes[0]?.sizeGroup ?? 'APPAREL');

  constructor() {
    toObservable(this.slug)
      .pipe(
        tap(() => this.status.set('loading')),
        switchMap((slug) => this.api.product(slug).pipe(catchError((err) => of({ error: err } as const)))),
        takeUntilDestroyed(),
      )
      .subscribe((result) => {
        if ('error' in result) {
          this.product.set(null);
          this.status.set(result.error?.status === 404 ? 'not-found' : 'error');
          this.title.setTitle(`Product not found | ${SITE.brandName}`);
          return;
        }
        this.show(result);
      });
  }

  protected isColorAvailable(code: string): boolean {
    const p = this.product();
    return p ? colorAvailable(p, code) : false;
  }

  protected isSizeAvailable(code: string): boolean {
    const p = this.product();
    return p ? sizeAvailable(p, code, this.color()) : false;
  }

  protected selectColor(code: string): void {
    this.color.set(code);
    this.imageIndex.set(0);
    const p = this.product()!;
    if (this.size() && !sizeAvailable(p, this.size()!, code)) {
      this.size.set(null);
    }
    this.size.set(this.size() ?? autoSize(p, code));
    this.clampQuantity();
  }

  protected selectSize(code: string): void {
    this.size.set(code);
    this.sizeError.set(false);
    this.clampQuantity();
  }

  protected changeQuantity(delta: number): void {
    const next = this.quantity() + delta;
    if (next >= 1 && next <= Math.max(1, this.maxQuantity())) {
      this.quantity.set(next);
    }
  }

  protected openSizeGuide(): void {
    this.dialog.open(SizeGuide, { data: { group: this.sizeGroup() }, width: '520px' });
  }

  protected addToBag(goToBag = false): void {
    const p = this.product();
    const variant = this.variant();
    if (!p) return;
    if (!this.size()) {
      this.sizeError.set(true);
      return;
    }
    if (!variant || !variant.inStock) return;
    const already = this.cart.quantityOf(variant.sku);
    const total = this.cart.add({
      sku: variant.sku,
      productId: p.id,
      slug: p.slug,
      name: p.name,
      imageUrl: this.mainImage()?.url,
      sizeCode: variant.sizeCode,
      colorCode: variant.colorCode,
      colorName: this.colorName(),
      unitPrice: p.price,
      quantity: this.quantity(),
      maxQuantity: variant.availableQuantity,
    });
    if (goToBag) {
      void this.router.navigateByUrl('/cart');
      return;
    }
    const capped = total < already + this.quantity();
    const ref = this.snackBar.open(
      capped ? `You have the maximum available (${total}) in your bag.` : `Added to your bag · ${p.name}`,
      'View bag',
      { duration: 4000 },
    );
    ref.onAction().subscribe(() => void this.router.navigateByUrl('/cart'));
  }

  protected loadMoreReviews(): void {
    const p = this.product();
    if (!p) return;
    this.reviewPage++;
    this.api.reviews(p.slug, this.reviewPage).subscribe((page) => this.reviews.set([...this.reviews(), ...page.content]));
  }

  private show(p: Product): void {
    this.product.set(p);
    this.status.set('ready');
    this.imageIndex.set(0);
    this.quantity.set(1);
    this.sizeError.set(false);
    const color = defaultColor(p);
    this.color.set(color);
    this.size.set(autoSize(p, color));

    this.title.setTitle(`${p.name} | ${SITE.brandName}`);
    this.meta.updateTag({ name: 'description', content: p.shortDescription ?? `${p.name} from ${SITE.brandName}.` });

    this.related.set([]);
    this.api.related(p.slug, 4).pipe(catchError(() => of([]))).subscribe((r) => this.related.set(r));
    this.reviewPage = 0;
    this.reviews.set([]);
    this.reviewTotal.set(0);
    if (p.reviewCount > 0) {
      this.api.reviews(p.slug, 0).pipe(catchError(() => of(null))).subscribe((page) => {
        this.reviews.set(page?.content ?? []);
        this.reviewTotal.set(page?.totalElements ?? 0);
      });
    }
  }

  private clampQuantity(): void {
    const max = this.maxQuantity();
    if (max > 0 && this.quantity() > max) this.quantity.set(max);
    if (this.quantity() < 1) this.quantity.set(1);
  }
}
