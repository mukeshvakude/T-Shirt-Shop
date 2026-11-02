package com.shop.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import com.shop.entity.ProductStatus;

/**
 * Filters for {@link ProductSearchRepository}. Null or empty values mean "no filter".
 *
 * @param statuses           allowed product statuses (public catalog: ACTIVE only)
 * @param activeCategoryOnly hide products whose category is inactive (public catalog)
 * @param inStockOnly        only products with at least one active variant in stock
 */
public record ProductSearchCriteria(
        String query,
        Long categoryId,
        String categorySlug,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        List<String> sizeCodes,
        List<String> colorCodes,
        Boolean featured,
        Boolean newArrival,
        boolean inStockOnly,
        Set<ProductStatus> statuses,
        boolean activeCategoryOnly,
        ProductSort sort) {

    public ProductSort sortOrDefault() {
        return sort != null ? sort : ProductSort.NEWEST;
    }
}
