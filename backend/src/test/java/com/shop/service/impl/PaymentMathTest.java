package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.shop.util.SecureTokens;

class PaymentMathTest {

    @Test
    void hmacMatchesThePublishedTestVector() {
        // RFC 4231, test case 2
        assertThat(SecureTokens.hmacSha256Hex("Jefe", "what do ya want for nothing?"))
                .isEqualTo("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843");
    }

    @Test
    void comparesSignaturesWithoutShortcuts() {
        assertThat(SecureTokens.constantTimeEquals("abc", "abc")).isTrue();
        assertThat(SecureTokens.constantTimeEquals("abc", "abd")).isFalse();
        assertThat(SecureTokens.constantTimeEquals(null, "abc")).isFalse();
    }

    @Test
    void convertsRupeesToPaise() {
        assertThat(PaymentServiceImpl.paise(new BigDecimal("1047.00"))).isEqualTo(104700L);
        assertThat(PaymentServiceImpl.paise(new BigDecimal("794.20"))).isEqualTo(79420L);
        assertThat(PaymentServiceImpl.paise(new BigDecimal("0.01"))).isEqualTo(1L);
    }
}
