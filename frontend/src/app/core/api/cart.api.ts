import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Cart, CartItemPatch } from '../models/cart.models';

export interface GuestItem {
  sku: string;
  quantity: number;
}

/** /api/cart: the signed-in customer's cart, plus a public quote for guest bags. */
@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/cart';

  get(): Observable<Cart> {
    return this.http.get<Cart>(this.base);
  }

  addProduct(sku: string, quantity: number): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/items`, { sku, quantity });
  }

  addDesign(designCode: string, sizeCode: string | null, quantity: number): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/items`, { designCode, sizeCode, quantity });
  }

  update(itemId: number, patch: CartItemPatch): Observable<Cart> {
    return this.http.patch<Cart>(`${this.base}/items/${itemId}`, patch);
  }

  remove(itemId: number): Observable<Cart> {
    return this.http.delete<Cart>(`${this.base}/items/${itemId}`);
  }

  clear(): Observable<Cart> {
    return this.http.delete<Cart>(this.base);
  }

  merge(items: GuestItem[]): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/merge`, { items });
  }

  applyCoupon(code: string): Observable<Cart> {
    return this.http.put<Cart>(`${this.base}/coupon`, { code });
  }

  removeCoupon(): Observable<Cart> {
    return this.http.delete<Cart>(`${this.base}/coupon`);
  }

  quote(items: GuestItem[]): Observable<Cart> {
    return this.http.post<Cart>(`${this.base}/quote`, { items });
  }
}
