import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ProductSummary } from '../../../core/models/store.models';
import { WishlistService } from '../../../core/wishlist.service';
import { ProductCard } from '../../../shared/product-card/product-card';

@Component({
  selector: 'app-wishlist-page',
  imports: [RouterLink, ProductCard],
  template: `
    <div class="page-wrap">
      <header>
        <h1>Wishlist</h1>
        <p class="muted">{{ visible().length }} saved product{{ visible().length === 1 ? '' : 's' }}</p>
      </header>
      @if (loading()) {
        <p class="muted">Loading…</p>
      } @else if (error()) {
        <p class="muted">Your wishlist could not be loaded. Please try again.</p>
      } @else if (visible().length === 0) {
        <div class="empty">
          <span class="material-symbols-outlined">favorite</span>
          <h2>Nothing saved yet</h2>
          <p>Tap the heart on any product to keep it here for later.</p>
          <a routerLink="/shop">Start shopping</a>
        </div>
      } @else {
        <div class="grid">
          @for (p of visible(); track p.id) { <app-product-card [product]="p" /> }
        </div>
      }
    </div>
  `,
  styles: `
    .page-wrap { max-width: 1360px; margin: 0 auto; padding: 32px 24px 0; }
    h1 { font-size: clamp(32px, 4vw, 46px); }
    header { margin-bottom: 28px; p { margin: 6px 0 0; } }
    .grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 36px 20px; }
    .empty { text-align: center; padding: 64px 16px; }
    .empty .material-symbols-outlined { font-size: 48px; }
    .empty p { color: var(--shop-muted); }
    .empty a { display: inline-flex; margin-top: 12px; height: 46px; align-items: center; padding: 0 24px;
      border-radius: 999px; background: #111; color: #fff; text-decoration: none; font-weight: 600; }
    @media (max-width: 1000px) { .grid { grid-template-columns: repeat(2, 1fr); gap: 28px 12px; } }
    @media (max-width: 600px) { .page-wrap { padding: 20px 16px 0; } }
  `,
})
export class WishlistPage implements OnInit {
  private readonly wishlist = inject(WishlistService);

  private readonly items = signal<ProductSummary[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal(false);
  /** Items un-hearted on this page disappear straight away. */
  protected readonly visible = computed(() => this.items().filter((p) => this.wishlist.has(p.id)));

  ngOnInit(): void {
    this.wishlist.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set(true);
      },
    });
  }
}
