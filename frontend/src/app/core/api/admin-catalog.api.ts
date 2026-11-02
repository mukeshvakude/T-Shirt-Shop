import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdminCategory,
  AdminProduct,
  AdminProductListItem,
  AdminProductQuery,
  AdminVariant,
  CategoryRequest,
  ImageUpdateRequest,
  Page,
  ProductImage,
  ProductRequest,
  ProductStatus,
  StockUpdateRequest,
  VariantRequest,
} from '../models/catalog.models';

/** Client for /api/admin/** product, category and inventory endpoints. */
@Injectable({ providedIn: 'root' })
export class AdminCatalogApi {
  private readonly http = inject(HttpClient);
  private readonly products = '/api/admin/products';
  private readonly categoriesUrl = '/api/admin/categories';

  // Products
  listProducts(query: AdminProductQuery): Observable<Page<AdminProductListItem>> {
    let params = new HttpParams().set('page', query.page).set('size', query.size);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.categoryId) params = params.set('categoryId', query.categoryId);
    if (query.status) params = params.set('status', query.status);
    if (query.sort) params = params.set('sort', query.sort);
    return this.http.get<Page<AdminProductListItem>>(this.products, { params });
  }

  getProduct(id: number): Observable<AdminProduct> {
    return this.http.get<AdminProduct>(`${this.products}/${id}`);
  }

  createProduct(body: ProductRequest): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(this.products, body);
  }

  updateProduct(id: number, body: ProductRequest): Observable<AdminProduct> {
    return this.http.put<AdminProduct>(`${this.products}/${id}`, body);
  }

  changeStatus(id: number, status: ProductStatus): Observable<AdminProduct> {
    return this.http.patch<AdminProduct>(`${this.products}/${id}/status`, { status });
  }

  deleteProduct(id: number): Observable<void> {
    return this.http.delete<void>(`${this.products}/${id}`);
  }

  replaceVariants(id: number, variants: VariantRequest[]): Observable<AdminVariant[]> {
    return this.http.put<AdminVariant[]>(`${this.products}/${id}/variants`, { variants });
  }

  uploadImage(id: number, file: File, colorId?: number | null, altText?: string): Observable<ProductImage> {
    const form = new FormData();
    form.append('file', file, file.name);
    let params = new HttpParams();
    if (colorId) params = params.set('colorId', colorId);
    if (altText?.trim()) params = params.set('altText', altText.trim());
    return this.http.post<ProductImage>(`${this.products}/${id}/images`, form, { params });
  }

  updateImage(id: number, imageId: number, body: ImageUpdateRequest): Observable<ProductImage> {
    return this.http.patch<ProductImage>(`${this.products}/${id}/images/${imageId}`, body);
  }

  deleteImage(id: number, imageId: number): Observable<void> {
    return this.http.delete<void>(`${this.products}/${id}/images/${imageId}`);
  }

  // Categories
  listCategories(): Observable<AdminCategory[]> {
    return this.http.get<AdminCategory[]>(this.categoriesUrl);
  }

  createCategory(body: CategoryRequest): Observable<AdminCategory> {
    return this.http.post<AdminCategory>(this.categoriesUrl, body);
  }

  updateCategory(id: number, body: CategoryRequest): Observable<AdminCategory> {
    return this.http.put<AdminCategory>(`${this.categoriesUrl}/${id}`, body);
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`${this.categoriesUrl}/${id}`);
  }

  // Inventory
  lowStock(page: number, size: number): Observable<Page<AdminVariant>> {
    return this.http.get<Page<AdminVariant>>('/api/admin/inventory/low-stock', {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  updateStock(inventoryId: number, body: StockUpdateRequest): Observable<AdminVariant> {
    return this.http.patch<AdminVariant>(`/api/admin/inventory/${inventoryId}`, body);
  }
}
