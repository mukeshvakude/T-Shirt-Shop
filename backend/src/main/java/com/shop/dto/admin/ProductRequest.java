package com.shop.dto.admin;

import java.math.BigDecimal;

import com.shop.entity.ProductStatus;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or update a product. Leave slug empty to generate it from the name. */
public record ProductRequest(
        @NotNull Long categoryId,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 170) @Pattern(regexp = Slugs.PATTERN, message = Slugs.MESSAGE) String slug,
        @Size(max = 300) String shortDescription,
        @Size(max = 10000) String description,
        @NotNull @DecimalMin("1.00") @DecimalMax("999999.99") @Digits(integer = 10, fraction = 2) BigDecimal basePrice,
        @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal discountPrice,
        @NotNull ProductStatus status,
        boolean featured,
        boolean newArrival,
        boolean customizable,
        @Size(max = 100) String material,
        @Size(max = 50) String fit,
        @Size(max = 300) String careInstructions) {
}
