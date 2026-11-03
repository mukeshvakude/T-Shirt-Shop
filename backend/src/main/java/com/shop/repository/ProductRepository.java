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

    /** Visible (ACTIVE, active category) products among the given ids, category fetched. */
    @Query("""
            select p from Product p join fetch p.category c
            where p.id in :ids and p.status = com.shop.entity.ProductStatus.ACTIVE and c.active = true""")
    List<Product> findVisibleByIds(@Param("ids") java.util.Collection<Long> ids);

    // ------------------------------------------------------------------ facets
    // Optional filters: pass null for category slug or text pattern to skip them.

    @Query("""
            select min(coalesce(p.discountPrice, p.basePrice)), max(coalesce(p.discountPrice, p.basePrice))
            from Product p join p.category c
            where p.status = com.shop.entity.ProductStatus.ACTIVE and c.active = true
              and (:slug is null or c.slug = :slug)
              and (:pattern is null or lower(p.name) like :pattern or lower(p.shortDescription) like :pattern
                   or lower(c.name) like :pattern)""")
    List<Object[]> priceRange(@Param("slug") String categorySlug, @Param("pattern") String pattern);

    @Query("""
            select s.code, s.label, count(distinct p.id) from Inventory i join i.product p join p.category c join i.size s
            where p.status = com.shop.entity.ProductStatus.ACTIVE and c.active = true and i.active = true
              and (i.quantityOnHand - i.quantityReserved) > 0
              and (:slug is null or c.slug = :slug)
              and (:pattern is null or lower(p.name) like :pattern or lower(p.shortDescription) like :pattern
                   or lower(c.name) like :pattern)
            group by s.code, s.label, s.sortOrder order by s.sortOrder""")
    List<Object[]> sizeFacets(@Param("slug") String categorySlug, @Param("pattern") String pattern);

    @Query("""
            select col.code, col.name, col.hex, count(distinct p.id) from Inventory i join i.product p join p.category c join i.color col
            where p.status = com.shop.entity.ProductStatus.ACTIVE and c.active = true and i.active = true
              and (i.quantityOnHand - i.quantityReserved) > 0
              and (:slug is null or c.slug = :slug)
              and (:pattern is null or lower(p.name) like :pattern or lower(p.shortDescription) like :pattern
                   or lower(c.name) like :pattern)
            group by col.code, col.name, col.hex, col.sortOrder order by col.sortOrder""")
    List<Object[]> colorFacets(@Param("slug") String categorySlug, @Param("pattern") String pattern);

    @Query("""
            select c.code, c.name, c.slug, count(p) from Product p join p.category c
            where p.status = com.shop.entity.ProductStatus.ACTIVE and c.active = true
              and (:pattern is null or lower(p.name) like :pattern or lower(p.shortDescription) like :pattern
                   or lower(c.name) like :pattern)
            group by c.code, c.name, c.slug, c.sortOrder order by c.sortOrder""")
    List<Object[]> categoryFacets(@Param("pattern") String pattern);

    /** Order lines reference products with RESTRICT, so ordered products can only be archived. */
    @Query(value = "select count(*) from order_items where product_id = :productId", nativeQuery = true)
    long countOrderItems(@Param("productId") Long productId);
}
