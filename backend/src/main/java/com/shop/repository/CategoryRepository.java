package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.shop.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByActiveTrueOrderBySortOrderAsc();

    List<Category> findAllByOrderBySortOrderAscNameAsc();

    Optional<Category> findBySlugAndActiveTrue(String slug);

    Optional<Category> findByCode(String code);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    boolean existsByCode(String code);

    /** Product counts per category id, in one query. */
    @Query("select p.category.id, count(p) from Product p group by p.category.id")
    List<Object[]> countProductsByCategory();
}
