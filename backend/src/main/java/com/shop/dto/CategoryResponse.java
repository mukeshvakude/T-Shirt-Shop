package com.shop.dto;

public record CategoryResponse(Long id, String code, String name, String slug, String description,
        String imageUrl, int sortOrder) {
}
