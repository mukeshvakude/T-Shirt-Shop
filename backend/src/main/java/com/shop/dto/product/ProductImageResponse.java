package com.shop.dto.product;

public record ProductImageResponse(Long id, String url, String altText, String colorCode, int sortOrder,
        boolean primary) {
}
