// Storefront types mirroring com.shop.dto.product.* and com.shop.dto.home.*.
import { Category } from './catalog.models';

export interface ColorSwatch {
  code: string;
  name: string;
  hex: string;
}

export interface ProductSummary {
  id: number;
  name: string;
  slug: string;
  categoryCode: string;
  categorySlug: string;
  categoryName: string;
  basePrice: number;
  discountPrice?: number;
  price: number;
  discountPercent: number;
  imageUrl?: string;
  avgRating: number;
  reviewCount: number;
  sizes: string[];
  colors: ColorSwatch[];
  inStock: boolean;
  featured: boolean;
  newArrival: boolean;
  customizable: boolean;
}

export interface CollectionSection {
  category: Category;
  productCount: number;
  products: ProductSummary[];
}

export interface CustomTshirtPromo {
  available: boolean;
  fromPrice?: number;
  tshirtTypes: string[];
}

export interface Offer {
  code: string;
  headline: string;
  description?: string;
  discountType: 'PERCENT' | 'FLAT';
  discountValue: number;
  minOrderAmount: number;
  maxDiscount?: number;
  validTo: string;
}

export interface ReviewHighlight {
  id: number;
  reviewerName: string;
  rating: number;
  title?: string;
  comment: string;
  productName: string;
  productSlug: string;
  createdAt: string;
}

export interface HomeData {
  categories: Category[];
  featured: ProductSummary[];
  newArrivals: ProductSummary[];
  bestSellers: ProductSummary[];
  onSale: ProductSummary[];
  collections: CollectionSection[];
  customTshirt: CustomTshirtPromo;
  offers: Offer[];
  reviews: ReviewHighlight[];
}

export interface ContactMessage {
  name: string;
  email: string;
  phone: string | null;
  subject: string;
  message: string;
  /** Honeypot: must stay empty. */
  website: string;
}

export interface VariantAvailability {
  sizeCode: string;
  colorCode: string;
  sku: string;
  /** Capped at the per-item order limit; exact stock is never exposed. */
  availableQuantity: number;
  inStock: boolean;
  lowStock: boolean;
}

export interface ProductImageView {
  id: number;
  url: string;
  altText?: string;
  colorCode?: string;
  sortOrder: number;
  primary: boolean;
}

export interface ProductDetail {
  id: number;
  name: string;
  slug: string;
  category: Category;
  shortDescription?: string;
  description?: string;
  basePrice: number;
  discountPrice?: number;
  price: number;
  discountPercent: number;
  material?: string;
  fit?: string;
  careInstructions?: string;
  avgRating: number;
  reviewCount: number;
  featured: boolean;
  newArrival: boolean;
  customizable: boolean;
  inStock: boolean;
  images: ProductImageView[];
  sizes: { id: number; code: string; label: string; sizeGroup: 'APPAREL' | 'WAIST'; sortOrder: number }[];
  colors: { id: number; code: string; name: string; hex: string; sortOrder: number }[];
  variants: VariantAvailability[];
}

export interface Facets {
  minPrice?: number;
  maxPrice?: number;
  sizes: { code: string; label: string; count: number }[];
  colors: { code: string; name: string; hex: string; count: number }[];
  categories: { code: string; name: string; slug: string; count: number }[];
}

export type CatalogSort = 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC' | 'POPULAR' | 'RATING' | 'NAME_ASC';

export interface CatalogQuery {
  category?: string | null;
  q?: string | null;
  sizes?: string[];
  colors?: string[];
  minPrice?: number | null;
  maxPrice?: number | null;
  inStock?: boolean;
  onSale?: boolean;
  sort?: CatalogSort;
  page?: number;
  size?: number;
}
