package com.shop.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class MoneyUtilsTest {

    @Test
    void convertsRupeesToPaise() {
        assertThat(MoneyUtils.toPaise(new BigDecimal("429.00"))).isEqualTo(42_900L);
        assertThat(MoneyUtils.toPaise(new BigDecimal("0.01"))).isEqualTo(1L);
        assertThat(MoneyUtils.toPaise(new BigDecimal("1999"))).isEqualTo(199_900L);
    }

    @Test
    void rejectsFractionsOfAPaisa() {
        assertThatThrownBy(() -> MoneyUtils.toPaise(new BigDecimal("10.005")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAndNullAmounts() {
        assertThatThrownBy(() -> MoneyUtils.toPaise(new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MoneyUtils.toPaise(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertsPaiseToRupees() {
        assertThat(MoneyUtils.fromPaise(42_900L)).isEqualByComparingTo("429.00");
        assertThat(MoneyUtils.fromPaise(42_900L).scale()).isEqualTo(2);
    }

    @Test
    void calculatesPercentRoundingHalfUp() {
        assertThat(MoneyUtils.percentOf(new BigDecimal("999.00"), new BigDecimal("10")))
                .isEqualByComparingTo("99.90");
        assertThat(MoneyUtils.percentOf(new BigDecimal("449.00"), new BigDecimal("15")))
                .isEqualByComparingTo("67.35");
        assertThat(MoneyUtils.percentOf(new BigDecimal("0.05"), new BigDecimal("50")))
                .isEqualByComparingTo("0.03");
    }

    @Test
    void normalizesToTwoDecimals() {
        assertThat(MoneyUtils.normalize(new BigDecimal("12.345")).toPlainString()).isEqualTo("12.35");
        assertThat(MoneyUtils.normalize(new BigDecimal("12")).toPlainString()).isEqualTo("12.00");
    }
}
