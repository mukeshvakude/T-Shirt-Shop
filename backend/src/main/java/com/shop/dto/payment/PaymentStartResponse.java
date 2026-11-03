package com.shop.dto.payment;

import java.time.Instant;

/**
 * What Razorpay Standard Checkout needs. {@code keyId} is the public Key ID; the Key Secret never
 * leaves the server. {@code amount} is in paise and comes from the order, not from the browser.
 */
public record PaymentStartResponse(
        String keyId,
        String razorpayOrderId,
        long amount,
        String currency,
        String orderNumber,
        String description,
        Prefill prefill,
        Instant holdUntil) {

    public record Prefill(String name, String email, String contact) {
    }
}
