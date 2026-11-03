package com.shop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.CouponRedemption;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    Optional<CouponRedemption> findByOrderId(Long orderId);
}
