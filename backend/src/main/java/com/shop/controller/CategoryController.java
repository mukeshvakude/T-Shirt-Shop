package com.shop.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.CategoryResponse;
import com.shop.service.ReferenceDataService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Public product categories")
@SecurityRequirements
public class CategoryController {

    private final ReferenceDataService referenceDataService;

    @GetMapping
    @Operation(summary = "List active categories in display order")
    public List<CategoryResponse> list() {
        return referenceDataService.getActiveCategories();
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Get one active category by its URL slug")
    public CategoryResponse get(
            @PathVariable @Pattern(regexp = "^[a-z0-9-]{1,100}$", message = "must be a lowercase slug") String slug) {
        return referenceDataService.getCategoryBySlug(slug);
    }
}
