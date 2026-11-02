package com.shop.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;

import com.shop.entity.ProductStatus;

public record AdminProductListItem(Long id, String name, String slug, Long categoryId, String categoryName,
        BigDecimal basePrice, BigDecimal discountPrice, ProductStatus status, boolean featured, boolean newArrival,
        String imageUrl, int totalAvailable, int activeVariants, boolean lowStock, int soldCount, Instant updatedAt) {
}
