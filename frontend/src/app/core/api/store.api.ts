import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ContactMessage, HomeData, Offer } from '../models/store.models';

/** Public storefront endpoints: home page, offers, newsletter and contact form. */
@Injectable({ providedIn: 'root' })
export class StoreApi {
  private readonly http = inject(HttpClient);

  home(): Observable<HomeData> {
    return this.http.get<HomeData>('/api/home');
  }

  offers(): Observable<Offer[]> {
    return this.http.get<Offer[]>('/api/offers');
  }

  subscribe(email: string): Observable<void> {
    return this.http.post<void>('/api/newsletter', { email });
  }

  contact(message: ContactMessage): Observable<void> {
    return this.http.post<void>('/api/contact', message);
  }
}
