import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Page } from '../models/catalog.models';
import { CheckoutSummary, OrderDetail, OrderSummary, PlaceOrderRequest } from '../models/order.models';

/** Checkout summary, placing orders and reading the customer's own orders. */
@Injectable({ providedIn: 'root' })
export class CheckoutApi {
  private readonly http = inject(HttpClient);

  summary(): Observable<CheckoutSummary> {
    return this.http.get<CheckoutSummary>('/api/checkout');
  }

  placeOrder(body: PlaceOrderRequest): Observable<OrderDetail> {
    return this.http.post<OrderDetail>('/api/checkout/place-order', body);
  }

  orders(page = 0, size = 10): Observable<Page<OrderSummary>> {
    return this.http.get<Page<OrderSummary>>('/api/orders', { params: new HttpParams().set('page', page).set('size', size) });
  }

  cancel(orderNumber: string, reason: string | null): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/orders/${encodeURIComponent(orderNumber)}/cancel`, { reason });
  }

  invoice(orderNumber: string): Observable<Blob> {
    return this.http.get(`/api/orders/${encodeURIComponent(orderNumber)}/invoice`, { responseType: 'blob' });
  }

  order(orderNumber: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`/api/orders/${encodeURIComponent(orderNumber)}`);
  }
}
