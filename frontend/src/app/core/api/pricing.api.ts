import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdminCharge,
  ChargeUpdate,
  PriceEstimateRequest,
  PriceQuote,
  PricingInfo,
  ShippingSettings,
} from '../models/pricing.models';

/** Live prices for custom T-Shirts (calculated by the server) and the admin price list. */
@Injectable({ providedIn: 'root' })
export class PricingApi {
  private readonly http = inject(HttpClient);

  info(): Observable<PricingInfo> {
    return this.http.get<PricingInfo>('/api/pricing/charges');
  }

  estimate(body: PriceEstimateRequest): Observable<PriceQuote> {
    return this.http.post<PriceQuote>('/api/pricing/design-estimate', body);
  }

  adminCharges(): Observable<AdminCharge[]> {
    return this.http.get<AdminCharge[]>('/api/admin/pricing/charges');
  }

  saveCharge(body: ChargeUpdate): Observable<AdminCharge> {
    return this.http.put<AdminCharge>('/api/admin/pricing/charges', body);
  }

  shipping(): Observable<ShippingSettings> {
    return this.http.get<ShippingSettings>('/api/admin/pricing/shipping');
  }

  updateShipping(body: ShippingSettings): Observable<ShippingSettings> {
    return this.http.put<ShippingSettings>('/api/admin/pricing/shipping', body);
  }
}
