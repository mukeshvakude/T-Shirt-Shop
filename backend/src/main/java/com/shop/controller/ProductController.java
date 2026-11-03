package com.shop.controller;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.PageResponse;
import com.shop.dto.product.ProductDetailResponse;
import com.shop.dto.product.ProductSummaryResponse;
import com.shop.exception.BadRequestException;
import com.shop.repository.ProductSearchCriteria;
import com.shop.repository.ProductSort;
import com.shop.service.CatalogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "Public catalog: listing, search, filters and product details")
@SecurityRequirements
public class ProductController {

    private static final String SLUG = "^[a-z0-9-]{1,170}$";
    private static final java.util.regex.Pattern CODE = java.util.regex.Pattern.compile("^[A-Z0-9_]{1,20}$");

    private final CatalogService catalogService;

    @GetMapping
    @Operation(summary = "Search and filter active products")
    public PageResponse<ProductSummaryResponse> list(
            @Parameter(description = "Text search in name, description and category")
            @RequestParam(required = false) @Size(max = 100) String q,
            @Parameter(description = "Category slug, e.g. t-shirts")
            @RequestParam(required = false) @Pattern(regexp = "^[a-z0-9-]{1,100}$") String category,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0") BigDecimal maxPrice,
            @Parameter(description = "Comma-separated size codes, e.g. M,L")
            @RequestParam(required = false) String sizes,
            @Parameter(description = "Comma-separated colour codes, e.g. BLACK,WHITE")
            @RequestParam(required = false) String colors,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) Boolean newArrival,
            @Parameter(description = "Only discounted products")
            @RequestParam(required = false) Boolean onSale,
            @RequestParam(defaultValue = "false") boolean inStock,
            @Parameter(description = "NEWEST, PRICE_ASC, PRICE_DESC, POPULAR, RATING, NAME_ASC")
            @RequestParam(defaultValue = "NEWEST") String sort,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(48) int size) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("INVALID_PRICE_RANGE", "minPrice must not be greater than maxPrice.");
        }
        ProductSearchCriteria criteria = new ProductSearchCriteria(q, null, category, minPrice, maxPrice,
                codes(sizes), codes(colors), featured, newArrival, onSale, inStock, null, true, parseSort(sort));
        return catalogService.search(criteria, page, size);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Product details with images, sizes, colours and availability")
    public ProductDetailResponse get(@PathVariable @Pattern(regexp = SLUG) String slug) {
        return catalogService.getBySlug(slug);
    }

    @GetMapping("/{slug}/related")
    @Operation(summary = "Popular products from the same category")
    public List<ProductSummaryResponse> related(@PathVariable @Pattern(regexp = SLUG) String slug,
            @RequestParam(defaultValue = "8") @Min(1) @Max(24) int limit) {
        return catalogService.getRelated(slug, limit);
    }

    static ProductSort parseSort(String value) {
        try {
            ProductSort sort = ProductSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
            if (sort == ProductSort.UPDATED_DESC) {
                throw new IllegalArgumentException();
            }
            return sort;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("INVALID_SORT",
                    "sort must be one of NEWEST, PRICE_ASC, PRICE_DESC, POPULAR, RATING, NAME_ASC.");
        }
    }

    static List<String> codes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        List<String> codes = Arrays.stream(csv.split(","))
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        if (codes.size() > 20 || codes.stream().anyMatch(c -> !CODE.matcher(c).matches())) {
            throw new BadRequestException("INVALID_FILTER", "Invalid size or colour filter.");
        }
        return codes;
    }
}
