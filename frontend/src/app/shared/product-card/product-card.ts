import { CurrencyPipe } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ProductSummary } from '../../core/models/store.models';
import { WishlistService } from '../../core/wishlist.service';
import { ImgFallback } from '../img-fallback.directive';
import { RatingStars } from '../rating-stars/rating-stars';

const MAX_SWATCHES = 5;

/** Product tile used by the home page and (from Phase 7) listings. */
@Component({
  selector: 'app-product-card',
  imports: [RouterLink, CurrencyPipe, ImgFallback, RatingStars],
  templateUrl: './product-card.html',
  styleUrl: './product-card.scss',
})
export class ProductCard {
  readonly product = input.required<ProductSummary>();
  /** Eager-load images for cards above the fold. */
  readonly priority = input(false);
  /** Hide the heart where it makes no sense (e.g. customizable blanks). */
  readonly showWishlist = input(true);

  protected readonly wishlist = inject(WishlistService);
  protected readonly saved = computed(() => this.wishlist.has(this.product().id));

  protected readonly link = computed(() =>
    this.product().customizable ? '/customize' : `/product/${this.product().slug}`,
  );
  protected readonly swatches = computed(() => this.product().colors.slice(0, MAX_SWATCHES));
  protected readonly moreColors = computed(() => Math.max(0, this.product().colors.length - MAX_SWATCHES));
  protected readonly sizeSummary = computed(() => {
    const sizes = this.product().sizes;
    if (sizes.length === 0) return '';
    if (sizes.length === 1) return sizes[0].replace(/^W/, '');
    return `${sizes[0].replace(/^W/, '')}–${sizes[sizes.length - 1].replace(/^W/, '')}`;
  });
}
