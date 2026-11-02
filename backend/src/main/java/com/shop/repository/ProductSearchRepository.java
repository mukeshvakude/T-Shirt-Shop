package com.shop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.shop.entity.Product;

/** Dynamic catalog search: text, category, price range, size, colour, flags, sorting, paging. */
public interface ProductSearchRepository {

    Page<Product> search(ProductSearchCriteria criteria, Pageable pageable);
}
