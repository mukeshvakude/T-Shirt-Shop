// Types mirroring the Spring Boot DTOs (com.shop.dto.*).

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
export const PRODUCT_STATUSES: readonly ProductStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'];

export type SizeGroup = 'APPAREL' | 'WAIST';

export type AdminProductSort = 'UPDATED_DESC' | 'NEWEST' | 'NAME_ASC' | 'PRICE_ASC' | 'PRICE_DESC' | 'POPULAR';

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface Category {
  id: number;
  code: string;
  name: string;
  slug: string;
  description?: string;
  imageUrl?: string;
  sortOrder: number;
}

export interface AdminCategory extends Category {
  active: boolean;
  system: boolean;
  productCount: number;
}

export interface CategoryRequest {
  name: string;
  slug: string | null;
  description: string | null;
  imageUrl: string | null;
  sortOrder: number;
  active: boolean;
}

export interface Size {
  id: number;
  code: string;
  label: string;
  sizeGroup: SizeGroup;
  sortOrder: number;
}

export interface Color {
  id: number;
  code: string;
  name: string;
  hex: string;
  sortOrder: number;
}

export interface ProductImage {
  id: number;
  url: string;
  altText?: string;
  colorCode?: string;
  sortOrder: number;
  primary: boolean;
}

export interface AdminVariant {
  id: number;
  sku: string;
  sizeId: number;
  sizeCode: string;
  colorId: number;
  colorCode: string;
  colorName: string;
  colorHex: string;
  quantityOnHand: number;
  quantityReserved: number;
  available: number;
  lowStockThreshold: number;
  lowStock: boolean;
  active: boolean;
  productId: number;
  productName: string;
}

export interface AdminProductListItem {
  id: number;
  name: string;
  slug: string;
  categoryId: number;
  categoryName: string;
  basePrice: number;
  discountPrice?: number;
  status: ProductStatus;
  featured: boolean;
  newArrival: boolean;
  imageUrl?: string;
  totalAvailable: number;
  activeVariants: number;
  lowStock: boolean;
  soldCount: number;
  updatedAt: string;
}

export interface AdminProduct {
  id: number;
  name: string;
  slug: string;
  categoryId: number;
  categoryName: string;
  categoryCode: string;
  shortDescription?: string;
  description?: string;
  basePrice: number;
  discountPrice?: number;
  status: ProductStatus;
  featured: boolean;
  newArrival: boolean;
  customizable: boolean;
  material?: string;
  fit?: string;
  careInstructions?: string;
  avgRating: number;
  reviewCount: number;
  soldCount: number;
  images: ProductImage[];
  variants: AdminVariant[];
  createdAt: string;
  updatedAt: string;
}

export interface ProductRequest {
  categoryId: number;
  name: string;
  slug: string | null;
  shortDescription: string | null;
  description: string | null;
  basePrice: number;
  discountPrice: number | null;
  status: ProductStatus;
  featured: boolean;
  newArrival: boolean;
  customizable: boolean;
  material: string | null;
  fit: string | null;
  careInstructions: string | null;
}

export interface VariantRequest {
  sizeId: number;
  colorId: number;
  quantityOnHand: number;
  lowStockThreshold: number;
  active: boolean;
}

export interface StockUpdateRequest {
  quantityOnHand?: number;
  adjustBy?: number;
  lowStockThreshold?: number;
  active?: boolean;
}

export interface ImageUpdateRequest {
  altText?: string | null;
  colorId: number | null;
  sortOrder?: number;
  primary?: boolean;
}

export interface AdminProductQuery {
  q?: string;
  categoryId?: number | null;
  status?: ProductStatus | null;
  sort?: AdminProductSort;
  page: number;
  size: number;
}
