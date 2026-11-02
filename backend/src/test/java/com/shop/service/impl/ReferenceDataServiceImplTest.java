package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shop.dto.CategoryResponse;
import com.shop.dto.SizeResponse;
import com.shop.entity.Category;
import com.shop.entity.ProductSize;
import com.shop.entity.SizeGroup;
import com.shop.exception.ResourceNotFoundException;
import com.shop.mapper.ReferenceDataMapperImpl;
import com.shop.repository.CategoryRepository;
import com.shop.repository.ProductColorRepository;
import com.shop.repository.ProductSizeRepository;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductSizeRepository sizeRepository;
    @Mock
    private ProductColorRepository colorRepository;

    private ReferenceDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ReferenceDataServiceImpl(categoryRepository, sizeRepository, colorRepository,
                new ReferenceDataMapperImpl());
    }

    @Test
    void returnsActiveCategoriesMappedToDtos() {
        when(categoryRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(category("TSHIRTS", "T-Shirts", "t-shirts", 1),
                        category("SHIRTS", "Shirts", "shirts", 2)));

        List<CategoryResponse> result = service.getActiveCategories();

        assertThat(result).extracting(CategoryResponse::code).containsExactly("TSHIRTS", "SHIRTS");
        assertThat(result.get(0).slug()).isEqualTo("t-shirts");
        assertThat(result.get(1).sortOrder()).isEqualTo(2);
    }

    @Test
    void findsCategoryBySlug() {
        when(categoryRepository.findBySlugAndActiveTrue("pants"))
                .thenReturn(Optional.of(category("PANTS", "Pants", "pants", 3)));

        assertThat(service.getCategoryBySlug("pants").name()).isEqualTo("Pants");
    }

    @Test
    void throwsNotFoundForUnknownSlug() {
        when(categoryRepository.findBySlugAndActiveTrue("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCategoryBySlug("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void filtersSizesByGroupWhenGiven() {
        when(sizeRepository.findAllBySizeGroupAndActiveTrueOrderBySortOrderAsc(SizeGroup.WAIST))
                .thenReturn(List.of(size("W32", SizeGroup.WAIST)));

        List<SizeResponse> result = service.getSizes(SizeGroup.WAIST);

        assertThat(result).extracting(SizeResponse::code).containsExactly("W32");
        verify(sizeRepository, never()).findAllByActiveTrueOrderBySortOrderAsc();
    }

    @Test
    void returnsAllSizesWithoutGroup() {
        when(sizeRepository.findAllByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(size("S", SizeGroup.APPAREL), size("W30", SizeGroup.WAIST)));

        assertThat(service.getSizes(null)).hasSize(2);
    }

    private static Category category(String code, String name, String slug, int sortOrder) {
        Category c = new Category();
        c.setCode(code);
        c.setName(name);
        c.setSlug(slug);
        c.setSortOrder(sortOrder);
        return c;
    }

    private static ProductSize size(String code, SizeGroup group) {
        ProductSize s = new ProductSize();
        s.setCode(code);
        s.setLabel(code);
        s.setSizeGroup(group);
        return s;
    }
}
