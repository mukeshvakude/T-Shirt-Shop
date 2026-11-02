package com.shop.dto.admin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The full list of size and colour combinations for a product. Combinations missing
 * from the list are deactivated, never deleted, because orders and carts may reference them.
 */
public record VariantsUpdateRequest(@NotNull @Size(max = 300) List<@Valid @NotNull VariantRequest> variants) {
}
