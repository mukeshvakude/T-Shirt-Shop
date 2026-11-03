import { AuthUser } from '../auth/auth.models';

export interface Address {
  id: number;
  label?: string;
  fullName: string;
  phone: string;
  line1: string;
  line2?: string;
  landmark?: string;
  city: string;
  state: string;
  pincode: string;
  country: string;
  isDefault: boolean;
}

export interface AddressRequest {
  label: string | null;
  fullName: string;
  phone: string;
  line1: string;
  line2: string | null;
  landmark: string | null;
  city: string;
  state: string;
  pincode: string;
  makeDefault: boolean;
}

export interface RecentOrder {
  orderNumber: string;
  placedAt: string;
  orderStatus: string;
  paymentStatus: string;
  grandTotal: number;
  itemCount: number;
}

export interface Dashboard {
  profile: AuthUser;
  orders: { total: number; active: number; delivered: number; cancelled: number };
  recentOrders: RecentOrder[];
  wishlistCount: number;
  addressCount: number;
  defaultAddress?: Address;
}

/** Customer-friendly wording for order statuses. */
export const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Awaiting payment',
  CONFIRMED: 'Confirmed',
  PROCESSING: 'Being prepared',
  SHIPPED: 'Shipped',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

/** "Opposite City Mall" stays as is; "Shivaji Park" becomes "Near Shivaji Park". */
export function landmarkText(landmark: string | null | undefined): string {
  const value = (landmark ?? '').trim();
  if (!value) return '';
  return /^(near|opp(osite)?|behind|beside|next to|in front of)\b/i.test(value) ? value : `Near ${value}`;
}
