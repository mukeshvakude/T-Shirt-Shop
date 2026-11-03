package com.shop.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.PageResponse;
import com.shop.dto.home.ReviewHighlight;
import com.shop.dto.product.FacetsResponse;
import com.shop.dto.product.ProductDetailResponse;
import com.shop.dto.product.ProductSummaryResponse;
import com.shop.entity.Inventory;
import com.shop.entity.Product;
import com.shop.entity.ProductImage;
import com.shop.entity.ProductStatus;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.ProductMapper;
import com.shop.repository.InventoryRepository;
import com.shop.repository.ProductImageRepository;
import com.shop.repository.ProductRepository;
import com.shop.repository.ProductSearchCriteria;
import com.shop.service.AppSettingService;
import com.shop.service.CatalogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {

    private static final String MAX_QTY_SETTING = "cart.max_quantity_per_item";

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductImageRepository imageRepository;
    private final ProductMapper mapper;
    private final AppSettingService settings;
    private final com.shop.repository.ReviewRepository reviewRepository;

    @Override
    public PageResponse<ProductSummaryResponse> search(ProductSearchCriteria criteria, int page, int size) {
        ProductSearchCriteria publicCriteria = new ProductSearchCriteria(criteria.query(), criteria.categoryId(),
                criteria.categorySlug(), criteria.minPrice(), criteria.maxPrice(), criteria.sizeCodes(),
                criteria.colorCodes(), criteria.featured(), criteria.newArrival(), criteria.onSale(), criteria.inStockOnly(),
                Set.of(ProductStatus.ACTIVE), true, criteria.sort());
        Page<Product> result = productRepository.search(publicCriteria, PageRequest.of(page, size));
        return toSummaryPage(result);
    }

    @Override
    public ProductDetailResponse getBySlug(String slug) {
        Product product = productRepository.findVisibleBySlug(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        List<Long> ids = List.of(product.getId());
        return mapper.toDetail(product, imageRepository.findForProducts(ids),
                inventoryRepository.findActiveForProducts(ids), settings.getInt(MAX_QTY_SETTING));
    }

    @Override
    public List<ProductSummaryResponse> getRelated(String slug, int limit) {
        Product product = productRepository.findVisibleBySlug(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        List<Product> related = productRepository.findRelated(product.getCategory().getId(), product.getId(),
                ProductStatus.ACTIVE, PageRequest.of(0, limit));
        return toSummaries(related);
    }

    private PageResponse<ProductSummaryResponse> toSummaryPage(Page<Product> page) {
        List<ProductSummaryResponse> content = toSummaries(page.getContent());
        return new PageResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    @Override
    public FacetsResponse facets(String categorySlug, String query) {
        String slug = categorySlug == null || categorySlug.isBlank() ? null : categorySlug;
        String pattern = likePattern(query);
        Object[] range = productRepository.priceRange(slug, pattern).stream().findFirst().orElse(new Object[2]);
        List<FacetsResponse.SizeFacet> sizes = productRepository.sizeFacets(slug, pattern).stream()
                .map(r -> new FacetsResponse.SizeFacet((String) r[0], (String) r[1], (Long) r[2]))
                .toList();
        List<FacetsResponse.ColorFacet> colors = productRepository.colorFacets(slug, pattern).stream()
                .map(r -> new FacetsResponse.ColorFacet((String) r[0], (String) r[1], (String) r[2], (Long) r[3]))
                .toList();
        List<FacetsResponse.CategoryFacet> categories = productRepository.categoryFacets(pattern).stream()
                .map(r -> new FacetsResponse.CategoryFacet((String) r[0], (String) r[1], (String) r[2], (Long) r[3]))
                .toList();
        return new FacetsResponse((BigDecimal) range[0], (BigDecimal) range[1], sizes, colors, categories);
    }

    @Override
    public PageResponse<ReviewHighlight> reviews(String slug, int page, int size) {
        Product product = productRepository.findVisibleBySlug(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Product", slug));
        return PageResponse.of(reviewRepository.findApprovedForProduct(product.getId(), PageRequest.of(page, size)),
                EngagementServiceImpl::toHighlight);
    }

    @Override
    public List<ProductSummaryResponse> summaries(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productRepository.findVisibleByIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        List<Product> ordered = productIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
        return toSummaries(ordered);
    }

    /** Lowercase LIKE pattern with wildcards escaped; null when there is no query. */
    static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.trim().toLowerCase(java.util.Locale.ROOT);
        q = q.length() > 100 ? q.substring(0, 100) : q;
        return "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Two batch queries (images, variants) for the whole page instead of two per product. */
    private List<ProductSummaryResponse> toSummaries(List<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<Long> ids = products.stream().map(Product::getId).toList();
        Map<Long, List<ProductImage>> images = imageRepository.findForProducts(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        Map<Long, List<Inventory>> variants = inventoryRepository.findActiveForProducts(ids).stream()
                .collect(Collectors.groupingBy(i -> i.getProduct().getId()));
        return products.stream()
                .map(p -> mapper.toSummary(p, images.getOrDefault(p.getId(), List.of()),
                        variants.getOrDefault(p.getId(), List.of())))
                .toList();
    }
}
