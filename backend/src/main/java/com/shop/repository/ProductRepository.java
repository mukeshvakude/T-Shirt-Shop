package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.Product;
import com.shop.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long>, ProductSearchRepository {

    @Query("select p from Product p join fetch p.category c where p.slug = :slug and p.status = :status and c.active = true")
    Optional<Product> findVisibleBySlug(@Param("slug") String slug, @Param("status") ProductStatus status);

    @Query("select p from Product p join fetch p.category where p.id = :id")
    Optional<Product> findWithCategoryById(@Param("id") Long id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, Long id);

    long countByCategoryId(Long categoryId);

    @Query("""
            select p from Product p join fetch p.category c
            where c.id = :categoryId and p.id <> :excludeId and p.status = :status
            order by p.soldCount desc, p.id desc""")
    List<Product> findRelated(@Param("categoryId") Long categoryId, @Param("excludeId") Long excludeId,
            @Param("status") ProductStatus status, Pageable pageable);

    /** Order lines reference products with RESTRICT, so ordered products can only be archived. */
    @Query(value = "select count(*) from order_items where product_id = :productId", nativeQuery = true)
    long countOrderItems(@Param("productId") Long productId);
}
