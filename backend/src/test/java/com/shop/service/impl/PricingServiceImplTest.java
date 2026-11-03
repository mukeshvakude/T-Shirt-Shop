package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class PricingServiceImplTest {

    @Test
    void roundsMoneyToPaise() {
        assertThat(PricingServiceImpl.money(new BigDecimal("49.005"))).isEqualByComparingTo("49.01");
        assertThat(PricingServiceImpl.money(new BigDecimal("299"))).hasToString("299.00");
        assertThat(PricingServiceImpl.money(null)).isNull();
    }

    @Test
    void labelsColourCodes() {
        assertThat(PricingServiceImpl.label("SKY_BLUE")).isEqualTo("Sky blue");
        assertThat(PricingServiceImpl.label("BLACK")).isEqualTo("Black");
        assertThat(PricingServiceImpl.label(null)).isEmpty();
    }
}
