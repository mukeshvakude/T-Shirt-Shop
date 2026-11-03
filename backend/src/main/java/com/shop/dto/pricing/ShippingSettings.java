package com.shop.dto.pricing;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/** Flat shipping charge and the order value from which shipping is free (INR). */
public record ShippingSettings(
        @NotNull @DecimalMin("0.00") @DecimalMax("10000.00") @Digits(integer = 8, fraction = 2) BigDecimal flatCharge,
        @NotNull @DecimalMin("0.00") @DecimalMax("1000000.00") @Digits(integer = 8, fraction = 2) BigDecimal freeThreshold) {
}
