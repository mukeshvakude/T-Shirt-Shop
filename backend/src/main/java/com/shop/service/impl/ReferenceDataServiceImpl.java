package com.shop.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shop.dto.CategoryResponse;
import com.shop.dto.ColorResponse;
import com.shop.dto.SizeResponse;
import com.shop.entity.SizeGroup;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.ReferenceDataMapper;
import com.shop.repository.CategoryRepository;
import com.shop.repository.ProductColorRepository;
import com.shop.repository.ProductSizeRepository;
import com.shop.service.ReferenceDataService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferenceDataServiceImpl implements ReferenceDataService {

    private final CategoryRepository categoryRepository;
    private final ProductSizeRepository sizeRepository;
    private final ProductColorRepository colorRepository;
    private final ReferenceDataMapper mapper;

    @Override
    public List<CategoryResponse> getActiveCategories() {
        return mapper.toCategoryResponses(categoryRepository.findAllByActiveTrueOrderBySortOrderAsc());
    }

    @Override
    public CategoryResponse getCategoryBySlug(String slug) {
        return categoryRepository.findBySlugAndActiveTrue(slug)
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Category", slug));
    }

    @Override
    public List<SizeResponse> getSizes(SizeGroup group) {
        return mapper.toSizeResponses(group == null
                ? sizeRepository.findAllByActiveTrueOrderBySortOrderAsc()
                : sizeRepository.findAllBySizeGroupAndActiveTrueOrderBySortOrderAsc(group));
    }

    @Override
    public List<ColorResponse> getColors() {
        return mapper.toColorResponses(colorRepository.findAllByActiveTrueOrderBySortOrderAsc());
    }
}
