package com.shop.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A failed attempt reported by Razorpay Checkout in the browser (informational; never marks anything paid). */
public record PaymentFailedRequest(
        @NotBlank @Pattern(regexp = "^TS-[0-9]{8}-[0-9]{6}$") String orderNumber,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^order_[A-Za-z0-9]+$") String razorpayOrderId,
        @Size(max = 50) String razorpayPaymentId,
        @Size(max = 80) String code,
        @Size(max = 300) String description) {
}
