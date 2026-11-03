package com.shop.payment;

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

    record GatewayOrder(String id, long amountPaise, String currency, String status) {
    }
}
