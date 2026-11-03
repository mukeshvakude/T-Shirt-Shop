package com.shop.service.impl;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.product.ProductSummaryResponse;
import com.shop.entity.Product;
import com.shop.entity.WishlistItem;
import com.shop.exception.BusinessRuleException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.ProductRepository;
import com.shop.repository.WishlistRepository;
import com.shop.service.CatalogService;
import com.shop.service.WishlistService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {

    static final int MAX_ITEMS = 200;

    private final WishlistRepository wishlist;
    private final ProductRepository products;
    private final CatalogService catalog;

    @Override
    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> list(Long userId) {
        return catalog.summaries(wishlist.findProductIds(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> productIds(Long userId) {
        return wishlist.findProductIds(userId);
    }

    @Override
    @Transactional
    public void add(Long userId, Long productId) {
        Product product = products.findVisibleByIds(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (wishlist.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        if (wishlist.countByUserId(userId) >= MAX_ITEMS) {
            throw new BusinessRuleException("WISHLIST_FULL",
                    "Your wishlist can hold up to " + MAX_ITEMS + " products. Remove some to add more.");
        }
        WishlistItem item = new WishlistItem();
        item.setUserId(userId);
        item.setProduct(product);
        try {
            wishlist.saveAndFlush(item);
        } catch (DataIntegrityViolationException alreadySaved) {
            // A parallel request saved the same product: the end state is what the user asked for.
        }
    }

    @Override
    @Transactional
    public void remove(Long userId, Long productId) {
        wishlist.deleteItem(userId, productId);
    }
}
