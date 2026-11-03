package com.shop.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaymentStartRequest(@NotBlank @Pattern(regexp = "^TS-[0-9]{8}-[0-9]{6}$") String orderNumber) {
}
