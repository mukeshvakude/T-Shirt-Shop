package com.shop.service;

import java.util.List;

import com.shop.dto.admin.AdminCategoryResponse;
import com.shop.dto.admin.CategoryRequest;

public interface AdminCategoryService {

    List<AdminCategoryResponse> list();

    AdminCategoryResponse create(CategoryRequest request);

    AdminCategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
