package com.shop.dto.pricing;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Choices that affect the price of a custom T-Shirt. Prices are never accepted from the client. */
public record PriceEstimateRequest(
        @NotBlank @Size(max = 40) String tshirtType,
        @NotBlank @Size(max = 20) String colorCode,
        @NotBlank @Size(max = 10) String sizeCode,
        @NotNull @Size(max = 2) List<@NotBlank @Size(max = 40) String> placements,
        boolean hasText,
        boolean premiumPrint,
        @Min(1) @Max(100) Integer quantity) {
}
