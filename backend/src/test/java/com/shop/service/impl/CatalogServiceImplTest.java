package com.shop.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogServiceImplTest {

    @Test
    void buildsEscapedLowercaseLikePatterns() {
        assertThat(CatalogServiceImpl.likePattern(null)).isNull();
        assertThat(CatalogServiceImpl.likePattern("   ")).isNull();
        assertThat(CatalogServiceImpl.likePattern(" Polo ")).isEqualTo("%polo%");
        assertThat(CatalogServiceImpl.likePattern("100%_cotton\\")).isEqualTo("%100\\%\\_cotton\\\\%");
        assertThat(CatalogServiceImpl.likePattern("x".repeat(300))).hasSize(102);
    }
}
