package com.shop.dto.admin;

public record AdminVariantResponse(Long id, String sku, Long sizeId, String sizeCode, Long colorId, String colorCode,
        String colorName, String colorHex, int quantityOnHand, int quantityReserved, int available,
        int lowStockThreshold, boolean lowStock, boolean active, Long productId, String productName) {
}
