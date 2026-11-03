package com.shop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.WishlistItem;

public interface WishlistRepository extends JpaRepository<WishlistItem, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    long countByUserId(Long userId);

    /** Product ids, most recently saved first. */
    @Query("select w.product.id from WishlistItem w where w.userId = :userId order by w.createdAt desc, w.id desc")
    List<Long> findProductIds(@Param("userId") Long userId);

    @Modifying
    @Query("delete from WishlistItem w where w.userId = :userId and w.product.id = :productId")
    int deleteItem(@Param("userId") Long userId, @Param("productId") Long productId);
}
