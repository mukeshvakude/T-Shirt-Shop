package com.shop.dto.pricing;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or change the charge for one choice (upsert by type and code). */
public record ChargeUpdateRequest(
        @NotBlank @Pattern(regexp = "TSHIRT_TYPE|PRINT_PLACEMENT|TEXT|DESIGN|SIZE|COLOR") String type,
        @NotBlank @Pattern(regexp = "^[A-Z0-9_]{1,40}$") String code,
        @NotNull @DecimalMin("0.00") @DecimalMax("100000.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
        boolean active,
        @Size(max = 200) String description) {
}
