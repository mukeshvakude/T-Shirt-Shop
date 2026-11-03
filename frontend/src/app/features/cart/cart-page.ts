import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router, RouterLink } from '@angular/router';

import { apiErrorMessage } from '../../core/api/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { CartItem, CartVariant, isBuyable } from '../../core/models/cart.models';
import { codeLabel } from '../../core/models/design.models';
import { NotifyService } from '../../core/notify.service';
import { ImgFallback } from '../../shared/img-fallback.directive';
import { InrPipe } from '../../shared/inr.pipe';

interface ColorChoice {
  code: string;
  name: string;
  hex: string | null;
  inStock: boolean;
}

/**
 * Shopping bag: products and custom T-Shirts together, with server prices, stock messages,
 * size/colour/quantity changes, coupons and the order summary.
 */
@Component({
  selector: 'app-cart-page',
  imports: [RouterLink, InrPipe, MatProgressSpinnerModule, ImgFallback],
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.scss',
})
export class CartPage implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly cartService = inject(CartService);
  private readonly notify = inject(NotifyService);
  private readonly router = inject(Router);

  protected readonly cart = this.cartService.cart;
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);
  protected readonly busy = signal<string | null>(null);
  protected readonly couponBusy = signal(false);
  protected readonly couponError = signal<string | null>(null);
  protected readonly checkingOut = signal(false);
  protected readonly dismissed = signal(false);

  protected readonly items = computed(() => this.cart()?.items ?? []);
  protected readonly blocked = computed(() => this.items().filter((i) => !isBuyable(i)).length);
  protected readonly label = codeLabel;
  protected readonly isBuyable = isBuyable;

  async ngOnInit(): Promise<void> {
    await this.reload();
  }

  protected async reload(): Promise<void> {
    this.loading.set(true);
    try {
      await this.cartService.refresh();
      this.loadError.set(null);
    } catch (error) {
      this.loadError.set(apiErrorMessage(error, 'Your bag could not be loaded.'));
    } finally {
      this.loading.set(false);
    }
  }

  // ---------------------------------------------------------------- options

  protected sizesFor(item: CartItem): CartVariant[] {
    return item.variants.filter((v) => v.colorCode === item.colorCode);
  }

  protected colorsFor(item: CartItem): ColorChoice[] {
    const map = new Map<string, ColorChoice>();
    for (const v of item.variants) {
      const c = map.get(v.colorCode) ?? { code: v.colorCode, name: v.colorName, hex: v.colorHex, inStock: false };
      c.inStock ||= v.inStock;
      map.set(v.colorCode, c);
    }
    return [...map.values()];
  }

  // ---------------------------------------------------------------- changes

  protected setQuantity(item: CartItem, value: number): void {
    const q = Math.round(Number(value));
    if (!Number.isFinite(q) || q < 1 || q === item.quantity) return;
    void this.change(item, { quantity: Math.min(q, Math.max(1, item.maxQuantity || item.quantity)) });
  }

  protected setSize(item: CartItem, sizeCode: string): void {
    if (sizeCode !== item.sizeCode) void this.change(item, { sizeCode });
  }

  protected setColor(item: CartItem, colorCode: string): void {
    if (colorCode === item.colorCode) return;
    const options = item.variants.filter((v) => v.colorCode === colorCode);
    const size = options.find((v) => v.sizeCode === item.sizeCode && v.inStock) ?? options.find((v) => v.inStock) ?? options[0];
    if (size) void this.change(item, { colorCode, sizeCode: size.sizeCode });
  }

  protected async remove(item: CartItem): Promise<void> {
    this.busy.set(item.key);
    try {
      await this.cartService.remove(item);
      this.notify.success(`Removed ${item.design ? 'your custom T-Shirt' : item.name} from your bag.`);
    } catch (error) {
      this.notify.error(error, 'The item could not be removed.');
    } finally {
      this.busy.set(null);
    }
  }

  private async change(item: CartItem, patch: { quantity?: number; sizeCode?: string; colorCode?: string }): Promise<void> {
    this.busy.set(item.key);
    try {
      await this.cartService.update(item, patch);
    } catch (error) {
      this.notify.error(error instanceof Error && !('status' in error) ? error.message : error, 'Your bag could not be updated.');
      await this.reload();
    } finally {
      this.busy.set(null);
    }
  }

  // ---------------------------------------------------------------- coupon

  protected async applyCoupon(code: string): Promise<void> {
    const value = code.trim();
    if (!value) return;
    this.couponBusy.set(true);
    this.couponError.set(null);
    try {
      const cart = await this.cartService.applyCoupon(value);
      this.notify.success(`Coupon ${cart.coupon?.code} applied.`);
    } catch (error) {
      this.couponError.set(apiErrorMessage(error, 'This coupon could not be applied.'));
    } finally {
      this.couponBusy.set(false);
    }
  }

  protected async removeCoupon(): Promise<void> {
    this.couponBusy.set(true);
    try {
      await this.cartService.removeCoupon();
      this.couponError.set(null);
    } catch (error) {
      this.notify.error(error);
    } finally {
      this.couponBusy.set(false);
    }
  }

  // ---------------------------------------------------------------- checkout

  /** Re-checks stock and prices on the server before moving on. */
  protected async checkout(): Promise<void> {
    if (!this.auth.isLoggedIn()) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl: '/cart' } });
      return;
    }
    this.checkingOut.set(true);
    try {
      const cart = await this.cartService.refresh();
      if (!cart.canCheckout) {
        this.notify.error('Some items in your bag changed. Please check the items marked below.');
        return;
      }
      void this.router.navigateByUrl('/checkout');
    } catch (error) {
      this.notify.error(error);
    } finally {
      this.checkingOut.set(false);
    }
  }

  protected itemLink(item: CartItem): string[] {
    return item.design ? ['/customize', item.design.code] : ['/product', item.productSlug];
  }
}
