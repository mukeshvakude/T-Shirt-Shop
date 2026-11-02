package com.shop.service;

import java.util.List;

import com.shop.dto.PageResponse;
import com.shop.dto.admin.AdminProductListItem;
import com.shop.dto.admin.AdminProductResponse;
import com.shop.dto.admin.AdminVariantResponse;
import com.shop.dto.admin.ImageUpdateRequest;
import com.shop.dto.admin.ProductRequest;
import com.shop.dto.admin.StockUpdateRequest;
import com.shop.dto.admin.VariantsUpdateRequest;
import com.shop.dto.product.ProductImageResponse;
import com.shop.entity.ProductStatus;
import com.shop.repository.ProductSearchCriteria;

public interface AdminProductService {

    PageResponse<AdminProductListItem> list(ProductSearchCriteria criteria, int page, int size);

    AdminProductResponse get(Long id);

    AdminProductResponse create(ProductRequest request);

    AdminProductResponse update(Long id, ProductRequest request);

    AdminProductResponse changeStatus(Long id, ProductStatus status);

    /** Hard delete for products that were never ordered; ordered products must be archived. */
    void delete(Long id);

    List<AdminVariantResponse> replaceVariants(Long productId, VariantsUpdateRequest request);

    AdminVariantResponse updateStock(Long inventoryId, StockUpdateRequest request);

    PageResponse<AdminVariantResponse> lowStock(int page, int size);

    ProductImageResponse addImage(Long productId, byte[] content, Long colorId, String altText);

    ProductImageResponse updateImage(Long productId, Long imageId, ImageUpdateRequest request);

    void deleteImage(Long productId, Long imageId);
}
