import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Category, Page } from '../models/catalog.models';
import { CatalogQuery, Facets, ProductDetail, ProductSummary, ReviewHighlight } from '../models/store.models';

/** Public catalog: listing, facets, product details, related products and reviews. */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  search(query: CatalogQuery): Observable<Page<ProductSummary>> {
    return this.http.get<Page<ProductSummary>>('/api/products', { params: toParams(query) });
  }

  facets(category: string | null | undefined, q: string | null | undefined): Observable<Facets> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    if (q?.trim()) params = params.set('q', q.trim());
    return this.http.get<Facets>('/api/products/facets', { params });
  }

  category(slug: string): Observable<Category> {
    return this.http.get<Category>(`/api/categories/${encodeURIComponent(slug)}`);
  }

  product(slug: string): Observable<ProductDetail> {
    return this.http.get<ProductDetail>(`/api/products/${encodeURIComponent(slug)}`);
  }

  related(slug: string, limit = 4): Observable<ProductSummary[]> {
    return this.http.get<ProductSummary[]>(`/api/products/${encodeURIComponent(slug)}/related`, {
      params: new HttpParams().set('limit', limit),
    });
  }

  reviews(slug: string, page: number, size = 5): Observable<Page<ReviewHighlight>> {
    return this.http.get<Page<ReviewHighlight>>(`/api/products/${encodeURIComponent(slug)}/reviews`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }
}

export function toParams(query: CatalogQuery): HttpParams {
  let params = new HttpParams();
  if (query.category) params = params.set('category', query.category);
  if (query.q?.trim()) params = params.set('q', query.q.trim());
  if (query.sizes?.length) params = params.set('sizes', query.sizes.join(','));
  if (query.colors?.length) params = params.set('colors', query.colors.join(','));
  if (query.minPrice != null) params = params.set('minPrice', query.minPrice);
  if (query.maxPrice != null) params = params.set('maxPrice', query.maxPrice);
  if (query.inStock) params = params.set('inStock', true);
  if (query.onSale) params = params.set('onSale', true);
  if (query.sort) params = params.set('sort', query.sort);
  params = params.set('page', query.page ?? 0).set('size', query.size ?? 12);
  return params;
}
