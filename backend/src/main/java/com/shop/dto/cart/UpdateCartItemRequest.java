package com.shop.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Change quantity, size or colour of a line. Omitted fields stay as they are. */
public record UpdateCartItemRequest(
        @Min(1) @Max(100) Integer quantity,
        @Size(max = 10) String sizeCode,
        @Size(max = 20) String colorCode) {
}
