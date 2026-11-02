package com.shop.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record VariantRequest(
        @NotNull Long sizeId,
        @NotNull Long colorId,
        @Min(0) @Max(100000) int quantityOnHand,
        @Min(0) @Max(10000) int lowStockThreshold,
        boolean active) {
}
