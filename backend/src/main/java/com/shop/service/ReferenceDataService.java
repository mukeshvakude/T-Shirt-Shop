package com.shop.service;

import java.util.List;

import com.shop.dto.CategoryResponse;
import com.shop.dto.ColorResponse;
import com.shop.dto.SizeResponse;
import com.shop.entity.SizeGroup;

public interface ReferenceDataService {

    List<CategoryResponse> getActiveCategories();

    CategoryResponse getCategoryBySlug(String slug);

    /** @param group optional filter, null returns all active sizes */
    List<SizeResponse> getSizes(SizeGroup group);

    List<ColorResponse> getColors();
}
