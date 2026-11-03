package com.shop.dto.cart;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Items from a guest bag kept in the browser: SKU and quantity only, never prices. */
public record GuestCartRequest(@NotNull @Size(max = 50) List<@Valid Item> items) {

    public record Item(@NotBlank @Size(max = 64) String sku, @Min(1) @Max(100) int quantity) {
    }
}
