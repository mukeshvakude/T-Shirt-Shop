package com.shop.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.ProductStatus;
import com.shop.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** Approved, well-rated reviews with a written comment, for active products, newest first. */
    @Query("""
            select r from Review r join fetch r.user join fetch r.product p
            where r.approved = true and r.rating >= :minRating and r.comment is not null
              and p.status = :status
            order by r.createdAt desc, r.id desc""")
    List<Review> findHighlights(@Param("minRating") int minRating, @Param("status") ProductStatus status,
            Pageable pageable);

    /** Approved reviews of one product, newest first. */
    @Query(value = """
            select r from Review r join fetch r.user join fetch r.product p
            where p.id = :productId and r.approved = true
            order by r.createdAt desc, r.id desc""",
            countQuery = "select count(r) from Review r where r.product.id = :productId and r.approved = true")
    org.springframework.data.domain.Page<Review> findApprovedForProduct(@Param("productId") Long productId,
            Pageable pageable);
}
