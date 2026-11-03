package com.shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @Query("""
            select distinct c from Cart c
            left join fetch c.items i
            left join fetch i.inventory inv
            left join fetch inv.product p
            left join fetch inv.size
            left join fetch inv.color
            left join fetch i.customDesign d
            where c.userId = :userId""")
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);
}
