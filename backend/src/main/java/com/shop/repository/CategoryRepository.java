package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByActiveTrueOrderBySortOrderAsc();

    Optional<Category> findBySlugAndActiveTrue(String slug);

    Optional<Category> findByCode(String code);
}
