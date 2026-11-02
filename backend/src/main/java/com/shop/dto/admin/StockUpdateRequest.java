package com.shop.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Change one SKU. Send either {@code quantityOnHand} (set) or {@code adjustBy}
 * (add or remove), plus an optional threshold and active flag.
 */
public record StockUpdateRequest(
        @Min(0) @Max(100000) Integer quantityOnHand,
        @Min(-100000) @Max(100000) Integer adjustBy,
        @Min(0) @Max(10000) Integer lowStockThreshold,
        Boolean active) {
}
