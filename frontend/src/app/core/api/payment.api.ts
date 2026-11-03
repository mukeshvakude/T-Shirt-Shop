import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { OrderDetail, PaymentStart, VerifyPaymentRequest } from '../models/order.models';

/** /api/payments: start or retry online payment, verify it, report failed attempts. */
@Injectable({ providedIn: 'root' })
export class PaymentApi {
  private readonly http = inject(HttpClient);

  start(orderNumber: string): Observable<PaymentStart> {
    return this.http.post<PaymentStart>('/api/payments/create-order', { orderNumber });
  }

  verify(body: VerifyPaymentRequest): Observable<OrderDetail> {
    return this.http.post<OrderDetail>('/api/payments/verify', body);
  }

  failed(body: { orderNumber: string; razorpayOrderId: string; razorpayPaymentId?: string; code?: string; description?: string }): Observable<OrderDetail> {
    return this.http.post<OrderDetail>('/api/payments/failed', body);
  }
}
