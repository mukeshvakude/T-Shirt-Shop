import { Page } from './catalog.models';
import { OrderDetail } from './order.models';

/** Admin portal (Phase 16). Mirrors com.shop.dto.admin.AdminViews. */

export interface Period {
  orders: number;
  sales: number;
  averageOrder: number;
}

export interface AdminOrderRow {
  orderNumber: string;
  placedAt: string;
  customerName: string;
  customerEmail: string;
  itemCount: number;
  total: number;
  status: string;
  paymentMethod: string;
  paymentStatus: string;
  needsReview: boolean;
}

export interface Dashboard {
  today: Period;
  last7Days: Period;
  last30Days: Period;
  toShip: number;
  awaitingPayment: number;
  paymentsNeedingReview: number;
  lowStockSkus: number;
  newMessages: number;
  newCustomers30Days: number;
  refunded30Days: number;
  ordersByStatus: { status: string; count: number }[];
  salesByDay: { day: string; orders: number; sales: number }[];
  topProducts: { productId: number; name: string; quantity: number; sales: number }[];
  recentOrders: AdminOrderRow[];
}

export interface AdminPaymentRow {
  id: number;
  orderNumber: string;
  attemptNo: number;
  provider: string;
  status: string;
  razorpayOrderId: string | null;
  razorpayPaymentId: string | null;
  method: string | null;
  methodDetail: string | null;
  amount: number;
  refundedAmount: number;
  needsReview: boolean;
  failureReason: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface AdminOrderDetail {
  order: OrderDetail;
  customerId: number;
  payments: AdminPaymentRow[];
  nextStatuses: string[];
}

export interface AdminPaymentDetail {
  payment: AdminPaymentRow;
  transactions: {
    at: string;
    type: string;
    status: string;
    source: string;
    reference: string | null;
    amount: number | null;
    details: string | null;
  }[];
}

export interface CustomerRow {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  enabled: boolean;
  joinedAt: string;
  lastLoginAt: string | null;
  orders: number;
  spent: number;
}

export interface CustomerDetail {
  customer: CustomerRow;
  addresses: number;
  designs: number;
  orders: AdminOrderRow[];
}

export interface MessageRow {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  subject: string;
  message: string;
  status: 'NEW' | 'READ' | 'REPLIED' | 'CLOSED';
  createdAt: string;
}

export type AdminPage<T> = Page<T>;

/** Button labels for the next status of an order. */
export const NEXT_STATUS_ACTIONS: Record<string, string> = {
  PROCESSING: 'Start preparing',
  SHIPPED: 'Mark shipped',
  OUT_FOR_DELIVERY: 'Out for delivery',
  DELIVERED: 'Mark delivered',
  CANCELLED: 'Cancel order',
};

export const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pending', CONFIRMED: 'Confirmed', PROCESSING: 'Preparing', SHIPPED: 'Shipped',
  OUT_FOR_DELIVERY: 'Out for delivery', DELIVERED: 'Delivered', CANCELLED: 'Cancelled',
  CREATED: 'Created', SUCCESS: 'Paid', FAILED: 'Failed', REFUNDED: 'Refunded',
  NEW: 'New', READ: 'Read', REPLIED: 'Replied', CLOSED: 'Closed',
};
