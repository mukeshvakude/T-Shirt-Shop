package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.shop.service.AppSettingService;

/**
 * Full application against the MySQL test database (tshirt_shop_test):
 * Flyway migrations, Hibernate schema validation, security and real endpoints.
 * Run with: ./mvnw verify
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppSettingService settings;

    @org.junit.jupiter.api.BeforeEach
    void cleanDatabase() {
        com.shop.support.DatabaseCleaner.clean(jdbc);
    }

    @Test
    void bootstrapAdminExists() {
        Integer admins = jdbc.queryForObject("SELECT COUNT(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id "
                + "JOIN roles r ON r.id = ur.role_id WHERE r.name = 'ROLE_ADMIN'", Integer.class);
        assertThat(admins).isEqualTo(1);
    }

    @Test
    void flywayAppliedOnlyProductionMigrations() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
        assertThat(flyway.info().pending()).isEmpty();
        Integer sampleProducts = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE slug = 'essential-crew-neck-tee'", Integer.class);
        assertThat(sampleProducts).as("dev sample data must not load outside the dev profile").isZero();
    }

    @Test
    void readsSettingsFromDatabase() {
        assertThat(settings.getDecimal("shipping.flat_charge")).isEqualByComparingTo("49.00");
        assertThat(settings.getDecimal("shipping.free_threshold")).isEqualByComparingTo("999.00");
        assertThat(settings.getInt("stock.reservation_minutes")).isEqualTo(15);
        assertThat(settings.getBoolean("cod.enabled")).isTrue();
    }

    @Test
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void listsTheFourCategoriesInOrder() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].code").value("TSHIRTS"))
                .andExpect(jsonPath("$[1].code").value("SHIRTS"))
                .andExpect(jsonPath("$[2].code").value("PANTS"))
                .andExpect(jsonPath("$[3].code").value("CUSTOM_TSHIRTS"));
    }

    @Test
    void getsCategoryBySlugAndReturns404ForUnknown() throws Exception {
        mockMvc.perform(get("/api/categories/customized-t-shirts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Customized T-Shirts"));

        mockMvc.perform(get("/api/categories/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returnsSizesAndColours() throws Exception {
        mockMvc.perform(get("/api/reference/sizes").param("group", "APPAREL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].code").value("S"))
                .andExpect(jsonPath("$[4].code").value("XXL"));

        mockMvc.perform(get("/api/reference/colors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(14)))
                .andExpect(jsonPath("$[0].hex").value("#FFFFFF"));
    }

    @Test
    void rejectsInvalidEnumParameter() throws Exception {
        mockMvc.perform(get("/api/reference/sizes").param("group", "HATS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void protectedAndAdminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsFrontendOriginOnly() throws Exception {
        mockMvc.perform(options("/api/categories")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));

        mockMvc.perform(options("/api/categories")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentIsPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("T-Shirt Shop API"))
                .andExpect(jsonPath("$.paths['/api/categories']").exists());
    }

    @Test
    void sendsSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("X-Request-Id"));
    }
}
