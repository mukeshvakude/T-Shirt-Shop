import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { StoreApi } from '../../core/api/store.api';
import { SITE, categoryLink } from '../../core/config/site.config';
import { HomeData, Offer } from '../../core/models/store.models';
import { ImgFallback } from '../../shared/img-fallback.directive';
import { NewsletterForm } from '../../shared/newsletter-form/newsletter-form';
import { ProductCard } from '../../shared/product-card/product-card';
import { RatingStars } from '../../shared/rating-stars/rating-stars';

/** Show whole rows of four on desktop: 5 to 7 items become 4, 9 to 11 become 8. */
export function fullRows<T>(items: T[], perRow = 4): T[] {
  return items.length <= perRow ? items : items.slice(0, items.length - (items.length % perRow));
}

@Component({
  selector: 'app-home',
  imports: [RouterLink, CurrencyPipe, DatePipe, ProductCard, RatingStars, NewsletterForm, ImgFallback],
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  private readonly api = inject(StoreApi);

  protected readonly site = SITE;
  protected readonly data = signal<HomeData | null>(null);
  protected readonly loading = signal(true);
  protected readonly failed = signal(false);
  protected readonly copied = signal<string | null>(null);
  protected readonly skeletons = Array.from({ length: 4 });

  /** Category tiles; the custom category tile opens the designer. */
  protected readonly tiles = computed(() =>
    (this.data()?.categories ?? []).map((c) => ({
      ...c,
      link: categoryLink(c),
      image: SITE.categoryImages[c.code] ?? null,
    })),
  );
  protected readonly firstShopCategory = computed(
    () => this.data()?.categories.find((c) => c.code === 'TSHIRTS') ?? this.data()?.categories[0],
  );
  protected link = categoryLink;
  protected rows = fullRows;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.failed.set(false);
    this.api.home().subscribe({
      next: (data) => {
        this.data.set(data);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      },
    });
  }

  protected async copy(offer: Offer): Promise<void> {
    try {
      await navigator.clipboard.writeText(offer.code);
      this.copied.set(offer.code);
      setTimeout(() => this.copied.set(null), 2000);
    } catch {
      this.copied.set(null);
    }
  }
}
