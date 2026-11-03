package com.shop.service;

import java.util.List;

import com.shop.dto.product.ProductSummaryResponse;

/** A customer's saved products. Only visible (active) products are returned. */
public interface WishlistService {

    List<ProductSummaryResponse> list(Long userId);

    List<Long> productIds(Long userId);

    /** Idempotent: saving an already saved product succeeds. */
    void add(Long userId, Long productId);

    /** Idempotent: removing a product that is not saved succeeds. */
    void remove(Long userId, Long productId);
}
