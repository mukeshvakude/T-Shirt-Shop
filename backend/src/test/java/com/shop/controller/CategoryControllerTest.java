package com.shop.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.shop.config.AppProperties;
import com.shop.config.CorsConfig;
import com.shop.config.RequestIdFilter;
import com.shop.dto.CategoryResponse;
import com.shop.exception.ResourceNotFoundException;
import com.shop.security.ProblemDetailSecurityHandler;
import com.shop.security.SecurityConfig;
import com.shop.service.ReferenceDataService;

/**
 * Web-layer slice: routing, validation, error format and security rules,
 * with the service mocked (no database).
 */
@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, ProblemDetailSecurityHandler.class, CorsConfig.class, RequestIdFilter.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.frontend-url=http://localhost:4200",
        "app.cors.allowed-origins=http://localhost:4200",
        "app.storage.provider=local",
        "app.storage.local-dir=./uploads",
        "app.stock.reservation-minutes=15",
        "app.api-docs-enabled=false"
})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReferenceDataService referenceDataService;

    @Test
    void listsCategoriesWithoutAuthentication() throws Exception {
        when(referenceDataService.getActiveCategories()).thenReturn(List.of(
                new CategoryResponse(1L, "TSHIRTS", "T-Shirts", "t-shirts", null, null, 1)));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TSHIRTS"))
                .andExpect(jsonPath("$[0].slug").value("t-shirts"))
                .andExpect(header().exists(RequestIdFilter.HEADER));
    }

    @Test
    void returnsProblemDetailForUnknownCategory() throws Exception {
        when(referenceDataService.getCategoryBySlug("ghost"))
                .thenThrow(new ResourceNotFoundException("Category", "ghost"));

        mockMvc.perform(get("/api/categories/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Category not found: ghost"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void rejectsInvalidSlugWithValidationErrors() throws Exception {
        mockMvc.perform(get("/api/categories/Not_A_Slug"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("slug"));
    }

    @Test
    void writesRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void reusesSafeIncomingRequestId() throws Exception {
        when(referenceDataService.getActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories").header(RequestIdFilter.HEADER, "client-abc-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "client-abc-123"));
    }

    @Test
    void replacesUnsafeIncomingRequestId() throws Exception {
        when(referenceDataService.getActiveCategories()).thenReturn(List.of());

        mockMvc.perform(get("/api/categories").header(RequestIdFilter.HEADER, "<script>"))
                .andExpect(header().string(RequestIdFilter.HEADER,
                        org.hamcrest.Matchers.matchesPattern("^[0-9a-f-]{36}$")));
    }
}
