import { PriceLine } from './pricing.models';

/** Shopping cart (Phase 11). Mirrors com.shop.dto.cart. Every amount is calculated by the server. */

export type CartLineStatus = 'OK' | 'LOW_STOCK' | 'NOT_ENOUGH_STOCK' | 'OUT_OF_STOCK' | 'UNAVAILABLE';

export interface CartVariant {
  sku: string;
  sizeCode: string;
  sizeLabel: string;
  colorCode: string;
  colorName: string;
  colorHex: string | null;
  inStock: boolean;
}

export interface CartDesign {
  code: string;
  name: string | null;
  previewFrontUrl: string | null;
  previewBackUrl: string | null;
  placements: string[];
  premiumPrint: boolean;
  texts: string[];
  basePrice: number;
  charges: PriceLine[];
}

export interface CartItem {
  /** Server line id; missing for guest bag lines (identified by key = SKU). */
  id?: number | null;
  key: string;
  type: 'PRODUCT' | 'CUSTOM';
  productId: number;
  productSlug: string;
  name: string;
  imageUrl: string | null;
  sku: string;
  sizeCode: string;
  sizeLabel: string;
  colorCode: string;
  colorName: string;
  colorHex: string | null;
  quantity: number;
  maxQuantity: number;
  unitPrice: number;
  unitMrp: number;
  lineTotal: number;
  status: CartLineStatus;
  message: string | null;
  variants: CartVariant[];
  design: CartDesign | null;
}

export interface CartCoupon {
  code: string;
  description: string | null;
  applied: boolean;
  discount: number;
  message: string | null;
}

export interface Cart {
  currency: string;
  items: CartItem[];
  itemCount: number;
  subtotal: number;
  coupon: CartCoupon | null;
  discount: number;
  shipping: number;
  total: number;
  freeShippingThreshold: number;
  amountToFreeShipping: number;
  taxInclusive: boolean;
  canCheckout: boolean;
  notices: string[];
}

export interface CartItemPatch {
  quantity?: number;
  sizeCode?: string;
  colorCode?: string;
}

export function isBuyable(item: CartItem): boolean {
  return item.status === 'OK' || item.status === 'LOW_STOCK';
}
