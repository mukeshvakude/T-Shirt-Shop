package com.shop.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.shop.dto.product.ProductImageResponse;
import com.shop.entity.ProductStatus;

public record AdminProductResponse(Long id, String name, String slug, Long categoryId, String categoryName,
        String categoryCode, String shortDescription, String description, BigDecimal basePrice,
        BigDecimal discountPrice, ProductStatus status, boolean featured, boolean newArrival, boolean customizable,
        String material, String fit, String careInstructions, BigDecimal avgRating, int reviewCount, int soldCount,
        List<ProductImageResponse> images, List<AdminVariantResponse> variants, Instant createdAt, Instant updatedAt) {
}
