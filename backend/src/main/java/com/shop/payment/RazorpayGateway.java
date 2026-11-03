package com.shop.payment;

import java.util.List;
import java.util.Map;

/** The calls the shop makes to Razorpay. Implemented over HTTPS; replaced by a stub in tests. */
public interface RazorpayGateway {

    /**
     * Creates a Razorpay order.
     *
     * @param amountPaise amount in the smallest currency unit (paise)
     * @param receipt     the shop order number (max 40 characters)
     */
    GatewayOrder createOrder(long amountPaise, String currency, String receipt, Map<String, String> notes);

    /** The payment as Razorpay sees it (the trusted source of truth). */
    GatewayPayment fetchPayment(String paymentId);

    /** All payments made against a Razorpay order (used by reconciliation). */
    List<GatewayPayment> fetchOrderPayments(String razorpayOrderId);

    /** Captures an authorized payment for its full amount. */
    GatewayPayment capture(String paymentId, long amountPaise, String currency);

    /** Full or partial refund; {@code receipt} identifies the refund on the shop side. */
    GatewayRefund refund(String paymentId, long amountPaise, String receipt, Map<String, String> notes);

    record GatewayOrder(String id, long amountPaise, String currency, String status) {
    }

    /**
     * @param status created, authorized, captured, refunded or failed
     * @param raw    the response without secrets, for the payment audit log
     */
    record GatewayPayment(String id, String orderId, long amountPaise, String currency, String status, String method,
            String methodDetail, String errorCode, String errorDescription, Map<String, Object> raw) {

        public boolean moneyReceived() {
            return "authorized".equals(status) || "captured".equals(status) || "refunded".equals(status);
        }
    }

    record GatewayRefund(String id, String paymentId, long amountPaise, String status) {
    }
}
