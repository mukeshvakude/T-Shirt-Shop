import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { Cart, CartItem } from '../models/cart.models';
import { CartService } from './cart.service';
import { CART_STORAGE_KEY, GuestCartService } from './guest-cart.service';

const line = {
  sku: 'TS-M-B', productId: 1, slug: 'tee', name: 'Tee', sizeCode: 'M', colorCode: 'BLACK', colorName: 'Black',
  unitPrice: 1, quantity: 2, maxQuantity: 5,
};

function cart(items: Partial<CartItem>[] = []): Cart {
  return {
    currency: 'INR', items: items as CartItem[], itemCount: items.reduce((s, i) => s + (i.quantity ?? 0), 0), subtotal: 0,
    coupon: null, discount: 0, shipping: 0, total: 0, freeShippingThreshold: 999, amountToFreeShipping: 0, taxInclusive: true,
    canCheckout: true, notices: [],
  };
}

describe('CartService', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.removeItem(CART_STORAGE_KEY);
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  it('keeps a guest bag in the browser and prices it on the server', async () => {
    const service = TestBed.inject(CartService);
    TestBed.tick();
    const result = await service.addProduct(line);
    expect(result).toEqual({ quantity: 2, capped: false, notices: [] });
    expect(service.count()).toBe(2);

    const pending = service.refresh();
    const req = http.expectOne('/api/cart/quote');
    expect(req.request.body).toEqual({ items: [{ sku: 'TS-M-B', quantity: 2 }] }); // no prices sent
    req.flush(cart([{ key: 'TS-M-B', sku: 'TS-M-B', quantity: 2 }]));
    expect((await pending).items.length).toBe(1);
  });

  it('switches a guest line to another size using the variant SKU', async () => {
    const service = TestBed.inject(CartService);
    TestBed.tick();
    await service.addProduct(line);
    const item = {
      id: null, key: 'TS-M-B', sku: 'TS-M-B', productId: 1, productSlug: 'tee', name: 'Tee', sizeCode: 'M', colorCode: 'BLACK',
      quantity: 2, unitPrice: 1, variants: [
        { sku: 'TS-M-B', sizeCode: 'M', sizeLabel: 'M', colorCode: 'BLACK', colorName: 'Black', colorHex: null, inStock: true },
        { sku: 'TS-L-B', sizeCode: 'L', sizeLabel: 'L', colorCode: 'BLACK', colorName: 'Black', colorHex: null, inStock: true },
        { sku: 'TS-XL-B', sizeCode: 'XL', sizeLabel: 'XL', colorCode: 'BLACK', colorName: 'Black', colorHex: null, inStock: false },
      ],
    } as unknown as CartItem;

    await expect(service.update(item, { sizeCode: 'XL' })).rejects.toThrow('out of stock');
    const pending = service.update(item, { sizeCode: 'L' });
    http.expectOne('/api/cart/quote').flush(cart());
    await pending;
    expect(TestBed.inject(GuestCartService).lines().map((l) => [l.sku, l.quantity])).toEqual([['TS-L-B', 2]]);
  });

  it('merges the guest bag into the account cart after sign-in', async () => {
    const service = TestBed.inject(CartService);
    TestBed.tick();
    await service.addProduct(line);

    TestBed.inject(AuthService).applySession({
      accessToken: 't', tokenType: 'Bearer', expiresIn: 900, expiresAt: '',
      user: { id: 1, fullName: 'Asha', email: 'a@example.com', phone: null, roles: ['ROLE_CUSTOMER'], emailVerified: false },
    } as never);
    TestBed.tick();

    const req = http.expectOne('/api/cart/merge');
    expect(req.request.body).toEqual({ items: [{ sku: 'TS-M-B', quantity: 2 }] });
    req.flush(cart([{ key: 'item-9', id: 9, sku: 'TS-M-B', quantity: 3, type: 'PRODUCT' }]));
    await vi.waitFor(() => expect(service.count()).toBe(3));
    expect(TestBed.inject(GuestCartService).lines()).toEqual([]);
  });
});
