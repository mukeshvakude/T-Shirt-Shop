import { Address, AddressRequest } from './account.models';
import { Cart } from './cart.models';
import { PriceLine } from './pricing.models';

/** Checkout and orders (Phase 12). Mirrors com.shop.dto.checkout and com.shop.dto.order. */

export type PaymentMethod = 'RAZORPAY' | 'COD';

export interface CheckoutTotals {
  itemsTotal: number;
  customizationTotal: number;
  shipping: number;
  discount: number;
  total: number;
}

export interface PaymentOption {
  method: PaymentMethod;
  available: boolean;
  message: string | null;
}

export interface CheckoutSummary {
  cart: Cart;
  totals: CheckoutTotals;
  contact: { name: string; email: string; phone: string | null };
  addresses: Address[];
  paymentOptions: PaymentOption[];
  reservationMinutes: number;
}

export interface PlaceOrderRequest {
  contact: { name: string; email: string; phone: string };
  addressId: number | null;
  address: AddressRequest | null;
  saveAddress: boolean;
  paymentMethod: PaymentMethod;
  note: string | null;
  /** The total the customer saw; the server compares it and never uses it as the price. */
  expectedTotal: number;
}

export interface OrderItem {
  name: string;
  sku: string;
  sizeCode: string;
  colorCode: string;
  colorName: string;
  imageUrl: string | null;
  quantity: number;
  unitBasePrice: number;
  unitCustomizationCharge: number;
  lineTotal: number;
  designCode: string | null;
  designName: string | null;
  charges: PriceLine[];
}

export interface OrderDetail {
  orderNumber: string;
  status: string;
  paymentStatus: string;
  paymentMethod: PaymentMethod;
  placedAt: string;
  reservationExpiresAt: string | null;
  awaitingPayment: boolean;
  items: OrderItem[];
  itemsTotal: number;
  customizationTotal: number;
  shipping: number;
  discount: number;
  couponCode: string | null;
  total: number;
  currency: string;
  contact: { name: string; email: string; phone: string };
  shippingAddress: {
    fullName: string;
    phone: string;
    line1: string;
    line2: string | null;
    landmark: string | null;
    city: string;
    state: string;
    pincode: string;
  };
  note: string | null;
  cancelReason: string | null;
  history: { status: string; at: string; note: string | null }[];
}

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  CREATED: 'Awaiting payment',
  PENDING: 'Pay on delivery',
  SUCCESS: 'Paid',
  FAILED: 'Payment failed',
  CANCELLED: 'Not paid',
  REFUNDED: 'Refunded',
};
