package com.shop.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The three values Razorpay Checkout returns to the browser after a successful payment. */
public record VerifyPaymentRequest(
        @NotBlank @Pattern(regexp = "^TS-[0-9]{8}-[0-9]{6}$") String orderNumber,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^order_[A-Za-z0-9]+$") String razorpayOrderId,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^pay_[A-Za-z0-9]+$") String razorpayPaymentId,
        @NotBlank @Size(max = 128) @Pattern(regexp = "^[0-9a-fA-F]+$") String razorpaySignature) {
}
