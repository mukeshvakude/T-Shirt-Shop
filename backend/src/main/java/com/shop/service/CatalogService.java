package com.shop.service;

import java.util.List;

import com.shop.dto.PageResponse;
import com.shop.dto.home.ReviewHighlight;
import com.shop.dto.product.FacetsResponse;
import com.shop.dto.product.ProductDetailResponse;
import com.shop.dto.product.ProductSummaryResponse;
import com.shop.repository.ProductSearchCriteria;

/** Public, read-only catalog: only ACTIVE products in active categories. */
public interface CatalogService {

    PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, int page, int size);

    ProductDetailResponse getBySlug(String slug);

    List<ProductSummaryResponse> getRelated(String slug, int limit);

    /** Filter options for a category page (slug) or a search (query); both optional. */
    FacetsResponse facets(String categorySlug, String query);

    /** Approved reviews of a product, newest first. */
    PageResponse<ReviewHighlight> reviews(String slug, int page, int size);

    /** Summaries of visible products, in the order of the given ids; hidden products are skipped. */
    List<ProductSummaryResponse> summaries(List<Long> productIds);
}
