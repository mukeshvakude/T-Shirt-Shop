package com.shop.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shop.config.AppProperties;
import com.shop.config.WhatsAppProperties;
import com.shop.dto.order.OrderResponse;

class WhatsAppMessagesTest {

    private final WhatsAppMessages messages = new WhatsAppMessages(new AppProperties("https://shop.example.in/", null, null, null,
            null, new AppProperties.Business("50% Tees", null, null, null, null, null), false));

    private static OrderResponse order(String status, String paymentMethod, String paymentStatus, String name) {
        return new OrderResponse("TS-20261104-123456", status, paymentStatus, paymentMethod, null, null, false, List.of(),
                null, null, null, null, null, new BigDecimal("125000.00"), "INR",
                new OrderResponse.Contact(name, "a@example.com", "9876543210", true), null, null, null, List.of(),
                null, null, null, null, "Ekart", null, false, false);
    }

    @Test
    void confirmedMessageMatchesItsTemplateValues() {
        WhatsAppMessages.Message m = messages.confirmed(order("CONFIRMED", "COD", "PENDING", "Neha Sharma"));

        assertThat(m.template()).isEqualTo("order_confirmed");
        assertThat(m.params()).containsExactly("Neha", "TS-20261104-123456", "Please keep ₹1,25,000 ready in cash at delivery.",
                "https://shop.example.in/order/TS-20261104-123456");
        assertThat(m.text()).isEqualTo("Hi Neha, thank you for shopping with 50% Tees! Your order TS-20261104-123456 is confirmed. "
                + "Please keep ₹1,25,000 ready in cash at delivery. Track it at https://shop.example.in/order/TS-20261104-123456 anytime.");
    }

    @Test
    void noTemplateStartsOrEndsWithAValue() {
        // Meta rejects template bodies that begin or end with a {{n}} variable
        OrderResponse o = order("SHIPPED", "RAZORPAY", "REFUNDED", "Neha");
        List<WhatsAppMessages.Message> all = List.of(messages.confirmed(o), messages.shipped(o), messages.outForDelivery(o),
                messages.delivered(o), messages.cancelled(o), messages.refund(o, BigDecimal.TEN), messages.newOrderForShop(o));
        for (WhatsAppMessages.Message m : all) {
            for (String p : m.params()) {
                assertThat(m.text()).as(m.template()).doesNotStartWith(p).doesNotEndWith(p);
            }
        }
    }

    @Test
    void templateValuesCannotBreakMetaRules() {
        assertThat(WhatsAppMessages.param("Line one\nline\ttwo     wide")).isEqualTo("Line one line two wide");
        assertThat(WhatsAppMessages.param(null)).isEmpty();
        assertThat(WhatsAppMessages.param("x".repeat(400))).hasSize(300);
    }

    @Test
    void adminGetsAStatusMessageAndAFreeTextStart() {
        List<WhatsAppMessages.Message> shipped = messages.forAdmin(order("SHIPPED", "RAZORPAY", "SUCCESS", ""));
        assertThat(shipped).extracting(WhatsAppMessages.Message::type).containsExactly("SHIPPED", "HELLO");
        assertThat(shipped.get(0).text()).startsWith("Hi there, your 50% Tees order").contains("with Ekart.");
        assertThat(shipped.get(1).text()).isEqualTo("Hi there, this is 50% Tees about your order TS-20261104-123456. ");

        assertThat(messages.forAdmin(order("PENDING", "RAZORPAY", "PENDING", "Neha"))).extracting(WhatsAppMessages.Message::type)
                .containsExactly("HELLO");
    }

    @Test
    void numbersAreNormalisedForWhatsApp() {
        assertThat(WhatsAppProperties.digits("+91 98765 43210")).isEqualTo("919876543210");
        assertThat(WhatsAppProperties.digits("098765 43210")).isEqualTo("919876543210");
        assertThat(WhatsAppProperties.digits("9876543210")).isEqualTo("919876543210");
        assertThat(WhatsAppProperties.digits("+44 7700 900123")).isEqualTo("447700900123");
        assertThat(WhatsAppProperties.digits("12345")).isNull();
        assertThat(WhatsAppProperties.digits("")).isNull();
        assertThat(WhatsAppProperties.digits(null)).isNull();
        assertThat(WhatsAppSender.mask("919876543210")).isEqualTo("91******3210");
    }

    @Test
    void cloudApiNeedsARealTokenAndNumberId() {
        assertThat(new WhatsAppProperties.CloudApi("change-me", "123456789", null, null).configured()).isFalse();
        assertThat(new WhatsAppProperties.CloudApi("EAAG" + "x".repeat(30), "abc", null, null).configured()).isFalse();
        WhatsAppProperties.CloudApi ok = new WhatsAppProperties.CloudApi("EAAG" + "x".repeat(30), "123456789012345", null, null);
        assertThat(ok.configured()).isTrue();
        assertThat(ok.base()).isEqualTo("https://graph.facebook.com/v21.0");
        assertThat(ok.language()).isEqualTo("en");
    }
}
