package com.shop.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Discount code. Redemption logic arrives with the cart in Phase 11. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    public enum DiscountType { PERCENT, FLAT }

    @Column(nullable = false, length = 40, unique = true)
    private String code;

    @Column(length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_order_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "max_discount", precision = 12, scale = 2)
    private BigDecimal maxDiscount;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit = 1;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(nullable = false)
    private boolean active = true;

    /** Advertised in the storefront Special offers section. */
    @Column(name = "is_public", nullable = false)
    private boolean publicOffer;

    @Version
    @Column(nullable = false)
    private Long version;
}
