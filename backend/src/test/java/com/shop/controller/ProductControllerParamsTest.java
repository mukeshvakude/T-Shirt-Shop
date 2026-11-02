package com.shop.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.shop.exception.BadRequestException;
import com.shop.repository.ProductSort;

class ProductControllerParamsTest {

    @Test
    void parsesSortCaseInsensitively() {
        assertThat(ProductController.parseSort("price_asc")).isEqualTo(ProductSort.PRICE_ASC);
        assertThat(ProductController.parseSort(" Popular ")).isEqualTo(ProductSort.POPULAR);
    }

    @Test
    void rejectsUnknownAndAdminOnlySorts() {
        assertThatThrownBy(() -> ProductController.parseSort("cheapest")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ProductController.parseSort("UPDATED_DESC")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void parsesCommaSeparatedCodes() {
        assertThat(ProductController.codes(" m, l ,M,,xl")).containsExactly("M", "L", "XL");
        assertThat(ProductController.codes(null)).isEmpty();
        assertThat(ProductController.codes("  ")).isEmpty();
    }

    @Test
    void rejectsSuspiciousCodes() {
        assertThatThrownBy(() -> ProductController.codes("M' OR 1=1")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> ProductController.codes("A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U"))
                .isInstanceOf(BadRequestException.class);
    }
}
