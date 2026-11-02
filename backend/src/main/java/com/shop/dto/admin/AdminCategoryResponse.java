package com.shop.dto.admin;

/** @param system built-in category the app depends on: its code is fixed and it cannot be deleted */
public record AdminCategoryResponse(Long id, String code, String name, String slug, String description,
        String imageUrl, int sortOrder, boolean active, boolean system, long productCount) {
}
