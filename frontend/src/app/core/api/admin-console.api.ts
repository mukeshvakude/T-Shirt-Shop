import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdminOrderDetail,
  AdminOrderRow,
  AdminPage,
  AdminPaymentDetail,
  AdminPaymentRow,
  AdminWhatsAppLinks,
  CustomerDetail,
  CustomerRow,
  Dashboard,
  MessageRow,
} from '../models/admin.models';
import { OrderDetail } from '../models/order.models';

function params(values: Record<string, string | number | boolean | null | undefined>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(values)) {
    if (v !== null && v !== undefined && v !== '' && v !== false) p = p.set(k, String(v));
  }
  return p;
}

export interface ListFilter {
  status?: string;
  q?: string;
  needsReview?: boolean;
  page?: number;
}

/** Admin portal APIs: dashboard, orders, payments, customers, contact messages. */
@Injectable({ providedIn: 'root' })
export class AdminConsoleApi {
  private readonly http = inject(HttpClient);

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/admin/dashboard');
  }

  orders(f: ListFilter): Observable<AdminPage<AdminOrderRow>> {
    return this.http.get<AdminPage<AdminOrderRow>>('/api/admin/orders', { params: params({ ...f, size: 20 }) });
  }

  order(number: string): Observable<AdminOrderDetail> {
    return this.http.get<AdminOrderDetail>(`/api/admin/orders/${encodeURIComponent(number)}`);
  }

  updateStatus(number: string, body: { status: string; courierName?: string; trackingNumber?: string; note?: string }): Observable<OrderDetail> {
    return this.http.patch<OrderDetail>(`/api/admin/orders/${encodeURIComponent(number)}/status`, body);
  }

  /** One-click WhatsApp messages (wa.me links with the text already typed) for the order's current state. */
  whatsapp(number: string): Observable<AdminWhatsAppLinks> {
    return this.http.get<AdminWhatsAppLinks>(`/api/admin/orders/${encodeURIComponent(number)}/whatsapp`);
  }

  invoice(number: string): Observable<Blob> {
    return this.http.get(`/api/admin/orders/${encodeURIComponent(number)}/invoice`, { responseType: 'blob' });
  }

  payments(f: ListFilter): Observable<AdminPage<AdminPaymentRow>> {
    return this.http.get<AdminPage<AdminPaymentRow>>('/api/admin/payments', { params: params({ ...f, size: 20 }) });
  }

  payment(id: number): Observable<AdminPaymentDetail> {
    return this.http.get<AdminPaymentDetail>(`/api/admin/payments/${id}`);
  }

  refund(id: number, reason: string): Observable<AdminPaymentDetail> {
    return this.http.post<AdminPaymentDetail>(`/api/admin/payments/${id}/refund`, { reason });
  }

  reviewed(id: number): Observable<AdminPaymentDetail> {
    return this.http.post<AdminPaymentDetail>(`/api/admin/payments/${id}/reviewed`, null);
  }

  customers(q: string, page: number): Observable<AdminPage<CustomerRow>> {
    return this.http.get<AdminPage<CustomerRow>>('/api/admin/customers', { params: params({ q, page, size: 20 }) });
  }

  customer(id: number): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`/api/admin/customers/${id}`);
  }

  setCustomerEnabled(id: number, enabled: boolean): Observable<CustomerDetail> {
    return this.http.patch<CustomerDetail>(`/api/admin/customers/${id}`, { enabled });
  }

  messages(status: string, page: number): Observable<AdminPage<MessageRow>> {
    return this.http.get<AdminPage<MessageRow>>('/api/admin/messages', { params: params({ status, page, size: 20 }) });
  }

  setMessageStatus(id: number, status: string): Observable<void> {
    return this.http.patch<void>(`/api/admin/messages/${id}`, { status });
  }
}
