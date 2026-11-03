import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CartApi, GuestItem } from '../api/cart.api';
import { AuthService } from '../auth/auth.service';
import { Cart, CartItem, CartItemPatch } from '../models/cart.models';
import { NotifyService } from '../notify.service';
import { CartLine, GuestCartService } from './guest-cart.service';

export interface AddResult {
  /** Quantity of this item now in the bag. */
  quantity: number;
  /** True when less than requested could be added. */
  capped: boolean;
  notices: string[];
}

/**
 * One bag for the whole storefront. Signed-in customers use the server cart; guests keep a bag in
 * this browser that the server prices on the cart page. On sign-in the guest bag is merged into
 * the account cart. Prices always come from the server.
 */
@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly api = inject(CartApi);
  private readonly auth = inject(AuthService);
  private readonly guest = inject(GuestCartService);
  private readonly notify = inject(NotifyService);

  private readonly state = signal<Cart | null>(null);
  readonly cart = this.state.asReadonly();
  readonly count = computed(() => (this.auth.isLoggedIn() ? (this.state()?.itemCount ?? 0) : this.guest.count()));

  constructor() {
    effect(() => {
      const signedIn = this.auth.isLoggedIn();
      untracked(() => {
        if (signedIn) {
          void this.syncAfterSignIn();
        } else {
          this.state.set(null);
        }
      });
    });
  }

  /** Current cart: the account cart, or the guest bag priced by the server. */
  async refresh(): Promise<Cart> {
    const cart = await firstValueFrom(this.auth.isLoggedIn() ? this.api.get() : this.api.quote(this.guestItems()));
    this.state.set(cart);
    return cart;
  }

  async addProduct(line: CartLine): Promise<AddResult> {
    if (!this.auth.isLoggedIn()) {
      const before = this.guest.quantityOf(line.sku);
      const quantity = this.guest.add(line);
      return { quantity, capped: quantity < before + line.quantity, notices: [] };
    }
    const before = this.quantityOf(line.sku);
    const cart = await this.apply(this.api.addProduct(line.sku, line.quantity));
    const quantity = this.quantityOf(line.sku);
    return { quantity, capped: quantity < before + line.quantity, notices: cart.notices };
  }

  /** Adds one of the customer's saved designs (sign-in required). */
  async addDesign(designCode: string, sizeCode: string | null, quantity: number): Promise<Cart> {
    return this.apply(this.api.addDesign(designCode, sizeCode, quantity));
  }

  async update(item: CartItem, patch: CartItemPatch): Promise<Cart> {
    if (item.id != null) {
      return this.apply(this.api.update(item.id, patch));
    }
    let sku = item.sku;
    if (patch.sizeCode || patch.colorCode) {
      const size = patch.sizeCode ?? item.sizeCode;
      const color = patch.colorCode ?? item.colorCode;
      const variant = item.variants.find((v) => v.sizeCode === size && v.colorCode === color);
      if (!variant) throw new Error('That size and colour is not available.');
      if (!variant.inStock) throw new Error('That size and colour is out of stock.');
      this.guest.replace(item.sku, {
        sku: variant.sku,
        productId: item.productId,
        slug: item.productSlug,
        name: item.name,
        sizeCode: variant.sizeCode,
        colorCode: variant.colorCode,
        colorName: variant.colorName,
        unitPrice: item.unitPrice,
        quantity: item.quantity,
        maxQuantity: 20,
      });
      sku = variant.sku;
    }
    if (patch.quantity) {
      this.guest.setQuantity(sku, patch.quantity);
    }
    return this.refresh();
  }

  async remove(item: CartItem): Promise<Cart> {
    if (item.id != null) {
      return this.apply(this.api.remove(item.id));
    }
    this.guest.remove(item.sku);
    return this.refresh();
  }

  applyCoupon(code: string): Promise<Cart> {
    return this.apply(this.api.applyCoupon(code));
  }

  removeCoupon(): Promise<Cart> {
    return this.apply(this.api.removeCoupon());
  }

  private quantityOf(sku: string): number {
    return (this.state()?.items ?? []).filter((i) => i.sku === sku && i.type === 'PRODUCT').reduce((s, i) => s + i.quantity, 0);
  }

  private async apply(request: ReturnType<CartApi['get']>): Promise<Cart> {
    const cart = await firstValueFrom(request);
    this.state.set(cart);
    return cart;
  }

  private guestItems(): GuestItem[] {
    return this.guest.lines().map((l) => ({ sku: l.sku, quantity: l.quantity }));
  }

  /** After sign-in: move the guest bag into the account cart, then show the account cart. */
  private async syncAfterSignIn(): Promise<void> {
    const items = this.guestItems();
    try {
      if (items.length) {
        const cart = await firstValueFrom(this.api.merge(items));
        this.guest.clear();
        this.state.set(cart);
        this.notify.success(items.length === 1 ? 'The item in your bag was saved to your account.' : 'The items in your bag were saved to your account.');
      } else {
        this.state.set(await firstValueFrom(this.api.get()));
      }
    } catch {
      // Keep the guest bag; the cart page reloads and shows any error.
    }
  }
}
