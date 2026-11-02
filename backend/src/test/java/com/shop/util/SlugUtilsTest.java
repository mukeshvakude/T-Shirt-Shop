package com.shop.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SlugUtilsTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Oxford Button-Down Shirt | oxford-button-down-shirt",
            "  Essential   Crew Neck Tee  | essential-crew-neck-tee",
            "Café Crème Polo | cafe-creme-polo",
            "100% Cotton -- Tee! | 100-cotton-tee",
            "T-Shirts | t-shirts"
    })
    void slugifies(String input, String expected) {
        assertThat(SlugUtils.slugify(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "!!!", "---"})
    void rejectsTextWithoutLettersOrDigits(String input) {
        assertThatThrownBy(() -> SlugUtils.slugify(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void limitsLength() {
        String slug = SlugUtils.slugify("a".repeat(300));
        assertThat(slug).hasSize(160);
    }
}
