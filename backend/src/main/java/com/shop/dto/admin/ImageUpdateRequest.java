package com.shop.dto.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Image metadata changes. {@code colorId} null removes the colour link. */
public record ImageUpdateRequest(
        @Size(max = 200) String altText,
        Long colorId,
        @Min(0) @Max(999) Integer sortOrder,
        Boolean primary) {
}
