package com.shop.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.CategoryResponse;
import com.shop.dto.PageResponse;
import com.shop.dto.home.HomeResponse;
import com.shop.dto.home.HomeResponse.CollectionSection;
import com.shop.dto.home.HomeResponse.CustomTshirtPromo;
import com.shop.dto.product.ProductSummaryResponse;
import com.shop.entity.CustomizationOption;
import com.shop.repository.CustomizationOptionRepository;
import com.shop.repository.ProductSearchCriteria;
import com.shop.repository.ProductSort;
import com.shop.service.CatalogService;
import com.shop.service.EngagementService;
import com.shop.service.HomeService;
import com.shop.service.ReferenceDataService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeServiceImpl implements HomeService {

    static final int SECTION_SIZE = 8;
    static final int COLLECTION_SIZE = 4;
    static final int REVIEW_COUNT = 6;
    /** Collections shown on the home page, in order. */
    static final List<String> COLLECTION_CODES = List.of("TSHIRTS", "SHIRTS", "PANTS");
    private static final String CUSTOM_CODE = "CUSTOM_TSHIRTS";

    private final CatalogService catalog;
    private final ReferenceDataService referenceData;
    private final EngagementService engagement;
    private final CustomizationOptionRepository customizationOptions;

    @Override
    public HomeResponse getHome() {
        List<CategoryResponse> categories = referenceData.getActiveCategories();
        Map<String, CategoryResponse> byCode = categories.stream()
                .collect(Collectors.toMap(CategoryResponse::code, Function.identity()));

        List<CollectionSection> collections = new ArrayList<>();
        for (String code : COLLECTION_CODES) {
            CategoryResponse category = byCode.get(code);
            if (category == null) {
                continue;
            }
            PageResponse<ProductSummaryResponse> page = catalog.search(
                    criteria().categorySlug(category.slug()).sort(ProductSort.POPULAR).build(), 0, COLLECTION_SIZE);
            if (!page.content().isEmpty()) {
                collections.add(new CollectionSection(category, page.totalElements(), page.content()));
            }
        }

        return new HomeResponse(
                categories,
                products(criteria().featured(true).sort(ProductSort.POPULAR).build(), false),
                products(criteria().newArrival(true).sort(ProductSort.NEWEST).build(), false),
                products(criteria().sort(ProductSort.POPULAR).build(), true),
                products(criteria().onSale(true).sort(ProductSort.POPULAR).build(), false),
                collections,
                customPromo(byCode.get(CUSTOM_CODE)),
                engagement.publicOffers(),
                engagement.reviewHighlights(REVIEW_COUNT));
    }

    private CustomTshirtPromo customPromo(CategoryResponse customCategory) {
        if (customCategory == null) {
            return new CustomTshirtPromo(false, null, List.of());
        }
        List<ProductSummaryResponse> blanks = catalog.search(
                criteria().categorySlug(customCategory.slug()).sort(ProductSort.PRICE_ASC).build(), 0, 20).content();
        BigDecimal fromPrice = blanks.stream().map(ProductSummaryResponse::price)
                .min(Comparator.naturalOrder()).orElse(null);
        List<String> types = customizationOptions
                .findAllByOptionTypeAndActiveTrueOrderBySortOrderAsc(CustomizationOption.TSHIRT_TYPE).stream()
                .map(CustomizationOption::getLabel)
                .toList();
        return new CustomTshirtPromo(!blanks.isEmpty(), fromPrice, types);
    }

    /**
     * Loads a little more than needed so that, when blank custom T-Shirts are excluded
     * (best sellers), the section is still full.
     */
    private List<ProductSummaryResponse> products(ProductSearchCriteria criteria, boolean excludeCustomBlanks) {
        return catalog.search(criteria, 0, SECTION_SIZE + 4).content().stream()
                .filter(p -> !excludeCustomBlanks || !CUSTOM_CODE.equals(p.categoryCode()))
                .limit(SECTION_SIZE)
                .toList();
    }

    private static CriteriaBuilder criteria() {
        return new CriteriaBuilder();
    }

    /** Small fluent helper so each home section reads clearly. */
    static final class CriteriaBuilder {
        private String categorySlug;
        private Boolean featured;
        private Boolean newArrival;
        private Boolean onSale;
        private ProductSort sort = ProductSort.NEWEST;

        CriteriaBuilder categorySlug(String value) { this.categorySlug = value; return this; }
        CriteriaBuilder featured(boolean value) { this.featured = value; return this; }
        CriteriaBuilder newArrival(boolean value) { this.newArrival = value; return this; }
        CriteriaBuilder onSale(boolean value) { this.onSale = value; return this; }
        CriteriaBuilder sort(ProductSort value) { this.sort = value; return this; }

        ProductSearchCriteria build() {
            return new ProductSearchCriteria(null, null, categorySlug, null, null, null, null, featured, newArrival,
                    onSale, false, null, true, sort);
        }
    }
}
