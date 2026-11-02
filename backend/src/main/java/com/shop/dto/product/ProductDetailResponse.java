package com.shop.dto.product;

import java.math.BigDecimal;
import java.util.List;

import com.shop.dto.CategoryResponse;
import com.shop.dto.ColorResponse;
import com.shop.dto.SizeResponse;

public record ProductDetailResponse(Long id, String name, String slug, CategoryResponse category,
        String shortDescription, String description, BigDecimal basePrice, BigDecimal discountPrice, BigDecimal price,
        int discountPercent, String material, String fit, String careInstructions, BigDecimal avgRating,
        int reviewCount, boolean featured, boolean newArrival, boolean customizable, boolean inStock,
        List<ProductImageResponse> images, List<SizeResponse> sizes, List<ColorResponse> colors,
        List<VariantAvailability> variants) {
}
