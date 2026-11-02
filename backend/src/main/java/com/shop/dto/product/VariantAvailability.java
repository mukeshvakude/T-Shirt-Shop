package com.shop.dto.product;

/**
 * Availability of one size and colour combination. The quantity is capped at the
 * maximum a customer may order, so exact stock levels are not exposed.
 */
public record VariantAvailability(String sizeCode, String colorCode, String sku, int availableQuantity,
        boolean inStock, boolean lowStock) {
}
