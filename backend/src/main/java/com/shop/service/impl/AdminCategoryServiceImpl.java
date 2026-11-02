package com.shop.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.admin.AdminCategoryResponse;
import com.shop.dto.admin.CategoryRequest;
import com.shop.entity.Category;
import com.shop.exception.ConflictException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.CategoryRepository;
import com.shop.repository.ProductRepository;
import com.shop.service.AdminCategoryService;
import com.shop.util.SlugUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminCategoryServiceImpl implements AdminCategoryService {

    /** Categories the application logic depends on (seeded by Flyway V2). */
    public static final Set<String> SYSTEM_CODES = Set.of("TSHIRTS", "SHIRTS", "PANTS", "CUSTOM_TSHIRTS");
    private static final int MAX_CODE_LENGTH = 40;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : categoryRepository.countProductsByCategory()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return categoryRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(c -> toResponse(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Override
    public AdminCategoryResponse create(CategoryRequest request) {
        String slug = isBlank(request.slug()) ? SlugUtils.slugify(request.name()) : request.slug();
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("SLUG_TAKEN", "Another category already uses the URL name '" + slug + "'.");
        }
        Category category = new Category();
        category.setCode(uniqueCode(slug));
        apply(category, request, slug);
        categoryRepository.save(category);
        return toResponse(category, 0);
    }

    @Override
    public AdminCategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        String slug = isBlank(request.slug()) ? category.getSlug() : request.slug();
        if (categoryRepository.existsBySlugAndIdNot(slug, id)) {
            throw new ConflictException("SLUG_TAKEN", "Another category already uses the URL name '" + slug + "'.");
        }
        apply(category, request, slug);
        return toResponse(category, productRepository.countByCategoryId(id));
    }

    @Override
    public void delete(Long id) {
        Category category = find(id);
        if (SYSTEM_CODES.contains(category.getCode())) {
            throw new ConflictException("CATEGORY_PROTECTED",
                    "Built-in categories cannot be deleted. You can deactivate it instead.");
        }
        if (productRepository.countByCategoryId(id) > 0) {
            throw new ConflictException("CATEGORY_NOT_EMPTY",
                    "Move or delete the products in this category first, or deactivate it.");
        }
        categoryRepository.delete(category);
    }

    private void apply(Category category, CategoryRequest request, String slug) {
        category.setName(request.name().trim());
        category.setSlug(slug);
        category.setDescription(blankToNull(request.description()));
        category.setImageUrl(blankToNull(request.imageUrl()));
        category.setSortOrder(request.sortOrder());
        category.setActive(request.active());
    }

    private String uniqueCode(String slug) {
        String base = slug.toUpperCase(Locale.ROOT).replace('-', '_');
        if (base.length() > MAX_CODE_LENGTH - 4) {
            base = base.substring(0, MAX_CODE_LENGTH - 4);
        }
        String code = base;
        for (int i = 2; categoryRepository.existsByCode(code); i++) {
            code = base + "_" + i;
        }
        return code;
    }

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category", id));
    }

    static AdminCategoryResponse toResponse(Category c, long productCount) {
        return new AdminCategoryResponse(c.getId(), c.getCode(), c.getName(), c.getSlug(), c.getDescription(),
                c.getImageUrl(), c.getSortOrder(), c.isActive(), SYSTEM_CODES.contains(c.getCode()), productCount);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
