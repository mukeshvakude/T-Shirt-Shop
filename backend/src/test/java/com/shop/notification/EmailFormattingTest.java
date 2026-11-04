package com.shop.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class EmailFormattingTest {

    @Test
    void masksAddressesInLogs() {
        assertThat(EmailSender.mask("neha@example.com")).isEqualTo("n***@example.com");
        assertThat(EmailSender.mask("a@b.in")).isEqualTo("***@b.in");
    }

    @Test
    void formatsRupees() {
        assertThat(EmailTemplates.money(new BigDecimal("1047.00"))).isEqualTo("₹1,047");
        assertThat(EmailTemplates.money(new BigDecimal("857.20"))).isEqualTo("₹857.20");
        assertThat(EmailTemplates.money(new BigDecimal("125000"))).isEqualTo("₹1,25,000");
    }
}
