package com.shop.entity;

/**
 * DRAFT: being prepared, hidden. ACTIVE: visible and purchasable.
 * INACTIVE: temporarily hidden. ARCHIVED: retired (kept because orders reference it).
 */
public enum ProductStatus {
    DRAFT,
    ACTIVE,
    INACTIVE,
    ARCHIVED
}
