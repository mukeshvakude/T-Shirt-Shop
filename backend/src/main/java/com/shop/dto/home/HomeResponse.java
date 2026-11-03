package com.shop.dto.home;

import java.math.BigDecimal;
import java.util.List;

import com.shop.dto.CategoryResponse;
import com.shop.dto.product.ProductSummaryResponse;

/**
 * Everything the storefront home page needs, in one request. Empty lists mean the
 * section should be hidden (for example, no approved reviews yet).
 */
public record HomeResponse(
        List<CategoryResponse> categories,
        List<ProductSummaryResponse> featured,
        List<ProductSummaryResponse> newArrivals,
        List<ProductSummaryResponse> bestSellers,
        List<ProductSummaryResponse> onSale,
        List<CollectionSection> collections,
        CustomTshirtPromo customTshirt,
        List<OfferResponse> offers,
        List<ReviewHighlight> reviews) {

    public record CollectionSection(CategoryResponse category, long productCount,
            List<ProductSummaryResponse> products) {
    }

    /** @param fromPrice lowest base price of an active customizable T-Shirt; null when none is active */
    public record CustomTshirtPromo(boolean available, BigDecimal fromPrice, List<String> tshirtTypes) {
    }
}
