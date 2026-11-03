package com.shop.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.id = :id")
    Optional<Coupon> findByIdForUpdate(@Param("id") Long id);

    /** How often this customer has used the coupon on placed orders. */
    @Query(value = "select count(*) from coupon_redemptions where coupon_id = :couponId and user_id = :userId", nativeQuery = true)
    long countRedemptions(@Param("couponId") Long couponId, @Param("userId") Long userId);

    /** Advertised coupons that are active, inside their validity window and not used up. */
    @Query("""
            select c from Coupon c
            where c.publicOffer = true and c.active = true and c.validFrom <= :now and c.validTo > :now
              and (c.usageLimit is null or c.usedCount < c.usageLimit)
            order by c.validTo asc""")
    List<Coupon> findPublicActive(@Param("now") Instant now);
}
