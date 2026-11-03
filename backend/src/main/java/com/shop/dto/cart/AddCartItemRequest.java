package com.shop.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Add a product variant ({@code sku}) or a saved design ({@code designCode}, optional {@code sizeCode}). */
public record AddCartItemRequest(
        @Size(max = 64) String sku,
        @Pattern(regexp = "^DSN-[A-Z0-9]{8}$") String designCode,
        @Size(max = 10) String sizeCode,
        @Min(1) @Max(100) int quantity) {
}
