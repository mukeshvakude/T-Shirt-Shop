package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.shop.entity.Coupon;

class EngagementServiceImplTest {

    @Test
    void showsOnlyFirstNameAndLastInitial() {
        assertThat(EngagementServiceImpl.publicName("Ananya Sharma")).isEqualTo("Ananya S.");
        assertThat(EngagementServiceImpl.publicName("  rohan   kumar mehta ")).isEqualTo("rohan M.");
        assertThat(EngagementServiceImpl.publicName("Priya")).isEqualTo("Priya");
        assertThat(EngagementServiceImpl.publicName(" ")).isEqualTo("Customer");
        assertThat(EngagementServiceImpl.publicName(null)).isEqualTo("Customer");
    }

    @Test
    void buildsOfferHeadlines() {
        assertThat(EngagementServiceImpl.toOffer(coupon(Coupon.DiscountType.PERCENT, "10.00")).headline())
                .isEqualTo("10% off");
        assertThat(EngagementServiceImpl.toOffer(coupon(Coupon.DiscountType.PERCENT, "12.50")).headline())
                .isEqualTo("12.5% off");
        assertThat(EngagementServiceImpl.toOffer(coupon(Coupon.DiscountType.FLAT, "100.00")).headline())
                .isEqualTo("₹100 off");
    }

    private static Coupon coupon(Coupon.DiscountType type, String value) {
        Coupon c = new Coupon();
        c.setCode("TEST");
        c.setDiscountType(type);
        c.setDiscountValue(new BigDecimal(value));
        return c;
    }
}
