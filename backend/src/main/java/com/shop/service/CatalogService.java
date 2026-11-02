package com.shop.service;

import java.util.List;

import com.shop.dto.PageResponse;
import com.shop.dto.product.ProductDetailResponse;
import com.shop.dto.product.ProductSummaryResponse;
import com.shop.repository.ProductSearchCriteria;

/** Public, read-only catalog: only ACTIVE products in active categories. */
public interface CatalogService {

    PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, int page, int size);

    ProductDetailResponse getBySlug(String slug);

    List<ProductSummaryResponse> getRelated(String slug, int limit);
}
