import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CheckoutSummary, OrderDetail, PlaceOrderRequest } from '../models/order.models';

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

  order(orderNumber: string): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`/api/orders/${encodeURIComponent(orderNumber)}`);
  }
}
