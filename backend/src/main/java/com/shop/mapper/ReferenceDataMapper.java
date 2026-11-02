package com.shop.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.shop.dto.CategoryResponse;
import com.shop.dto.ColorResponse;
import com.shop.dto.SizeResponse;
import com.shop.entity.Category;
import com.shop.entity.ProductColor;
import com.shop.entity.ProductSize;

@Mapper
public interface ReferenceDataMapper {

    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toCategoryResponses(List<Category> categories);

    SizeResponse toResponse(ProductSize size);

    List<SizeResponse> toSizeResponses(List<ProductSize> sizes);

    ColorResponse toResponse(ProductColor color);

    List<ColorResponse> toColorResponses(List<ProductColor> colors);
}
