/** Dynamic pricing (Phase 10). Mirrors com.shop.dto.pricing. Prices are always calculated by the server. */

export type ChargeType = 'TSHIRT_TYPE' | 'PRINT_PLACEMENT' | 'TEXT' | 'DESIGN' | 'SIZE' | 'COLOR';

export interface PriceLine {
  type: ChargeType;
  code: string;
  label: string;
  amount: number;
}

export interface PriceQuote {
  currency: string;
  productSlug: string;
  baseMrp: number;
  basePrice: number;
  charges: PriceLine[];
  customizationPerUnit: number;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  shippingEstimate: number;
  freeShippingThreshold: number;
  amountToFreeShipping: number;
  taxInclusive: boolean;
}

export interface PricingInfo {
  charges: { type: ChargeType; code: string; amount: number }[];
  shippingFlatCharge: number;
  freeShippingThreshold: number;
  maxQuantityPerItem: number;
}

export interface PriceEstimateRequest {
  tshirtType: string;
  colorCode: string;
  sizeCode: string;
  placements: string[];
  hasText: boolean;
  premiumPrint: boolean;
  quantity: number;
}

export interface AdminCharge {
  id: number | null;
  type: ChargeType;
  code: string;
  label: string;
  amount: number;
  active: boolean;
  description: string | null;
  updatedAt: string | null;
}

export interface ChargeUpdate {
  type: ChargeType;
  code: string;
  amount: number;
  active: boolean;
  description?: string | null;
}

export interface ShippingSettings {
  flatCharge: number;
  freeThreshold: number;
}

export const CHARGE_GROUPS: { type: ChargeType; title: string; hint: string }[] = [
  { type: 'TSHIRT_TYPE', title: 'T-Shirt style', hint: 'Added to the blank T-Shirt price for each style' },
  { type: 'PRINT_PLACEMENT', title: 'Print areas', hint: 'Charged for every print area used' },
  { type: 'TEXT', title: 'Custom text', hint: 'Charged once when the design has any text' },
  { type: 'DESIGN', title: 'Premium print', hint: 'Charged when the customer picks premium print' },
  { type: 'SIZE', title: 'Sizes', hint: 'Extra charge for larger sizes' },
  { type: 'COLOR', title: 'Colours', hint: 'Extra charge for special colours' },
];
