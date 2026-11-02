package com.shop.repository;

/** Catalog sort options. Price sorts use the effective price (discount price when set). */
public enum ProductSort {
    NEWEST,
    PRICE_ASC,
    PRICE_DESC,
    POPULAR,
    RATING,
    NAME_ASC,
    UPDATED_DESC
}
