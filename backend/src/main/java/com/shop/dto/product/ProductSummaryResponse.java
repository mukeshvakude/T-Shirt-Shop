package com.shop.dto.product;

import java.math.BigDecimal;
import java.util.List;

/** One card in a product listing. {@code price} is what the customer pays. */
public record ProductSummaryResponse(Long id, String name, String slug, String categoryCode, String categorySlug,
        String categoryName, BigDecimal basePrice, BigDecimal discountPrice, BigDecimal price, int discountPercent,
        String imageUrl, BigDecimal avgRating, int reviewCount, List<String> sizes, List<ColorSwatch> colors,
        boolean inStock, boolean featured, boolean newArrival, boolean customizable) {
}
