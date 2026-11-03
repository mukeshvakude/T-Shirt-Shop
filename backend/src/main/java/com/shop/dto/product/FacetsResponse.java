package com.shop.dto.product;

import java.math.BigDecimal;
import java.util.List;

/**
 * Filter options for a listing (a category or a search). Counts are numbers of
 * products that have at least one in-stock variant with that size or colour.
 */
public record FacetsResponse(BigDecimal minPrice, BigDecimal maxPrice, List<SizeFacet> sizes,
        List<ColorFacet> colors, List<CategoryFacet> categories) {

    public record SizeFacet(String code, String label, long count) {
    }

    public record ColorFacet(String code, String name, String hex, long count) {
    }

    public record CategoryFacet(String code, String name, String slug, long count) {
    }
}
