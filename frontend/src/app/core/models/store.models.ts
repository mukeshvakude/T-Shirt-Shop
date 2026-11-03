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
