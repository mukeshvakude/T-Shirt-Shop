package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.support.TestImages;

/**
 * Product management end to end against MySQL: admin category and product CRUD,
 * variants and stock, image upload and serving, and the public catalog with filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIT {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    private String admin;
    private long tshirts;
    private long pants;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        admin = token(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"admin@test.local\",\"password\":\"TestAdmin123\"}")).andReturn());
        tshirts = id("SELECT id FROM categories WHERE code = 'TSHIRTS'");
        pants = id("SELECT id FROM categories WHERE code = 'PANTS'");
    }

    // ---------------------------------------------------------------- categories

    @Test
    void adminManagesCategories() throws Exception {
        MvcResult created = mvc.perform(asAdmin(post("/api/admin/categories"))
                        .content("{\"name\":\"Hoodies & Sweats\",\"sortOrder\":5,\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("hoodies-sweats"))
                .andExpect(jsonPath("$.code").value("HOODIES_SWEATS"))
                .andExpect(jsonPath("$.system").value(false))
                .andReturn();
        long id = idOf(created);

        mvc.perform(asAdmin(post("/api/admin/categories")).content("{\"name\":\"Hoodies Sweats\",\"slug\":\"hoodies-sweats\",\"sortOrder\":6,\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLUG_TAKEN"));

        mvc.perform(asAdmin(put("/api/admin/categories/" + id)).content("{\"name\":\"Hoodies\",\"slug\":\"hoodies\",\"sortOrder\":5,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(get("/api/categories")).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(asAdmin(get("/api/admin/categories"))).andExpect(jsonPath("$", hasSize(5)));

        mvc.perform(asAdmin(delete("/api/admin/categories/" + tshirts)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_PROTECTED"));
        mvc.perform(asAdmin(delete("/api/admin/categories/" + id))).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- products, variants, catalog

    @Test
    void productLifecycleFromDraftToPublicCatalog() throws Exception {
        mvc.perform(asAdmin(post("/api/admin/products")).content(productJson(tshirts, "Night Sky Tee", "799.00", "599.00", "ACTIVE")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_PUBLISHABLE"));
        mvc.perform(asAdmin(post("/api/admin/products")).content(productJson(tshirts, "Night Sky Tee", "799.00", "799.00", "DRAFT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DISCOUNT_NOT_LOWER"));

        long productId = idOf(mvc.perform(asAdmin(post("/api/admin/products"))
                        .content(productJson(tshirts, "Night Sky Tee", "799.00", "599.00", "DRAFT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("night-sky-tee"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());

        mvc.perform(get("/api/products/night-sky-tee")).andExpect(status().isNotFound());

        mvc.perform(asAdmin(put("/api/admin/products/" + productId + "/variants"))
                        .content(variantsJson(new String[][] {{"W32", "BLACK", "10"}})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SIZE_NOT_ALLOWED"));
        mvc.perform(asAdmin(put("/api/admin/products/" + productId + "/variants"))
                        .content(variantsJson(new String[][] {{"M", "BLACK", "1"}, {"M", "BLACK", "2"}})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DUPLICATE_VARIANT"));

        mvc.perform(asAdmin(put("/api/admin/products/" + productId + "/variants"))
                        .content(variantsJson(new String[][] {
                                {"S", "BLACK", "10"}, {"M", "BLACK", "40"}, {"M", "WHITE", "0"}, {"L", "WHITE", "3"}})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].sku", startsWith("TS-")));

        mvc.perform(asAdmin(patch("/api/admin/products/" + productId + "/status")).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(get("/api/products").param("category", "t-shirts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Night Sky Tee"))
                .andExpect(jsonPath("$.content[0].price").value(599.00))
                .andExpect(jsonPath("$.content[0].discountPercent").value(25))
                .andExpect(jsonPath("$.content[0].sizes", contains("S", "M", "L")))
                .andExpect(jsonPath("$.content[0].colors[*].code", contains("WHITE", "BLACK")))
                .andExpect(jsonPath("$.content[0].inStock").value(true));

        mvc.perform(get("/api/products/night-sky-tee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category.code").value("TSHIRTS"))
                .andExpect(jsonPath("$.variants", hasSize(4)))
                .andExpect(jsonPath("$.variants[?(@.sizeCode=='M' && @.colorCode=='BLACK')].availableQuantity").value(20))
                .andExpect(jsonPath("$.variants[?(@.sizeCode=='M' && @.colorCode=='WHITE')].inStock").value(false))
                .andExpect(jsonPath("$.variants[?(@.sizeCode=='L' && @.colorCode=='WHITE')].lowStock").value(true));
    }

    @Test
    void catalogFiltersSortsAndPaginates() throws Exception {
        long cheap = publishedProduct(tshirts, "Basic White Tee", "399.00", null, new String[][] {{"M", "WHITE", "5"}});
        publishedProduct(tshirts, "Premium Black Tee", "1299.00", "999.00", new String[][] {{"L", "BLACK", "5"}});
        publishedProduct(pants, "Navy Chinos", "1499.00", null, new String[][] {{"W32", "NAVY", "5"}});
        long soldOut = publishedProduct(tshirts, "Sold Out Tee", "499.00", null, new String[][] {{"S", "RED", "1"}});
        jdbc.update("UPDATE inventory SET quantity_on_hand = 0 WHERE product_id = ?", soldOut);

        mvc.perform(get("/api/products").param("sort", "price_asc").param("category", "t-shirts"))
                .andExpect(jsonPath("$.content[*].name", contains("Basic White Tee", "Sold Out Tee", "Premium Black Tee")));
        mvc.perform(get("/api/products").param("sort", "PRICE_DESC").param("q", "tee").param("inStock", "true"))
                .andExpect(jsonPath("$.content[*].name", contains("Premium Black Tee", "Basic White Tee")));
        mvc.perform(get("/api/products").param("sizes", "W32"))
                .andExpect(jsonPath("$.content[*].name", contains("Navy Chinos")));
        mvc.perform(get("/api/products").param("colors", "black,white").param("sizes", "M"))
                .andExpect(jsonPath("$.content[*].name", contains("Basic White Tee")));
        mvc.perform(get("/api/products").param("minPrice", "900").param("maxPrice", "1000"))
                .andExpect(jsonPath("$.content[*].name", contains("Premium Black Tee")));
        mvc.perform(get("/api/products").param("q", "chino"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/products").param("size", "2").param("page", "1").param("sort", "NAME_ASC")
                        .param("category", "t-shirts"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[*].name", contains("Sold Out Tee")));
        mvc.perform(get("/api/products").param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE_RANGE"));
        mvc.perform(get("/api/products").param("q", "100%_\\"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mvc.perform(get("/api/products/basic-white-tee/related"))
                .andExpect(jsonPath("$[*].name", hasItem("Premium Black Tee")));
        assertThat(cheap).isPositive();
    }

    @Test
    void stockUpdatesRespectReservations() throws Exception {
        long productId = publishedProduct(tshirts, "Stock Tee", "500.00", null, new String[][] {{"M", "BLACK", "10"}});
        long inventoryId = id("SELECT id FROM inventory WHERE product_id = " + productId);

        mvc.perform(asAdmin(patch("/api/admin/inventory/" + inventoryId)).content("{\"adjustBy\":-4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(6));
        mvc.perform(asAdmin(patch("/api/admin/inventory/" + inventoryId)).content("{\"adjustBy\":-7}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"));

        jdbc.update("UPDATE inventory SET quantity_reserved = 3 WHERE id = ?", inventoryId);
        mvc.perform(asAdmin(patch("/api/admin/inventory/" + inventoryId)).content("{\"quantityOnHand\":2}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOCK_BELOW_RESERVED"));
        mvc.perform(asAdmin(patch("/api/admin/inventory/" + inventoryId)).content("{\"quantityOnHand\":5,\"lowStockThreshold\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(2))
                .andExpect(jsonPath("$.lowStock").value(true));

        mvc.perform(asAdmin(get("/api/admin/inventory/low-stock")).param("size", "200"))
                .andExpect(jsonPath("$.content[*].sku", hasItem(containsString(String.format("%05d", productId)))));
        jdbc.update("UPDATE inventory SET quantity_reserved = 0 WHERE id = ?", inventoryId);
    }

    @Test
    void imageUploadValidationServingAndDeletion() throws Exception {
        long productId = publishedProduct(tshirts, "Picture Tee", "500.00", null, new String[][] {{"M", "BLACK", "10"}});

        MvcResult first = mvc.perform(asAdminMultipart(productId, "front.png", TestImages.png(64, 64)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", startsWith("/media/products/")))
                .andExpect(jsonPath("$.primary").value(true))
                .andReturn();
        String url = jsonValue(first, "url");
        long firstId = idOf(first);

        long secondId = idOf(mvc.perform(asAdminMultipart(productId, "back.jpg", TestImages.jpeg(64, 64)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primary").value(false))
                .andReturn());

        mvc.perform(asAdminMultipart(productId, "evil.png", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TYPE_NOT_ALLOWED"));

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/media/products/..%2F..%2Fpom.xml")).andExpect(status().is4xxClientError());

        mvc.perform(asAdmin(patch("/api/admin/products/" + productId + "/images/" + secondId)).content("{\"primary\":true}"))
                .andExpect(jsonPath("$.primary").value(true));
        mvc.perform(get("/api/products/picture-tee"))
                .andExpect(jsonPath("$.images[?(@.primary==true)].id").value((int) secondId));

        mvc.perform(asAdmin(delete("/api/admin/products/" + productId + "/images/" + firstId))).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteAndArchive() throws Exception {
        long productId = publishedProduct(tshirts, "Delete Me Tee", "500.00", null, new String[][] {{"M", "BLACK", "1"}});

        mvc.perform(asAdmin(patch("/api/admin/products/" + productId + "/status")).content("{\"status\":\"ARCHIVED\"}"))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/products/delete-me-tee")).andExpect(status().isNotFound());

        mvc.perform(asAdmin(delete("/api/admin/products/" + productId))).andExpect(status().isNoContent());
        mvc.perform(asAdmin(get("/api/admin/products/" + productId))).andExpect(status().isNotFound());
    }

    @Test
    void customersCannotUseAdminApis() throws Exception {
        String customer = token(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Patil\",\"email\":\"asha@example.com\",\"password\":\"Secret123\"}")).andReturn());
        mvc.perform(get("/api/admin/products").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/categories").header("Authorization", "Bearer " + customer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private long publishedProduct(long categoryId, String name, String base, String discount, String[][] variants)
            throws Exception {
        long id = idOf(mvc.perform(asAdmin(post("/api/admin/products"))
                        .content(productJson(categoryId, name, base, discount, "DRAFT")))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(asAdmin(put("/api/admin/products/" + id + "/variants")).content(variantsJson(variants)))
                .andExpect(status().isOk());
        mvc.perform(asAdmin(patch("/api/admin/products/" + id + "/status")).content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        return id;
    }

    private String productJson(long categoryId, String name, String base, String discount, String status) {
        return "{\"categoryId\":" + categoryId + ",\"name\":\"" + name + "\",\"shortDescription\":\"Soft cotton\","
                + "\"basePrice\":" + base + ",\"discountPrice\":" + (discount == null ? "null" : discount)
                + ",\"status\":\"" + status + "\",\"featured\":false,\"newArrival\":true,\"customizable\":false}";
    }

    private String variantsJson(String[][] variants) {
        StringBuilder sb = new StringBuilder("{\"variants\":[");
        for (int i = 0; i < variants.length; i++) {
            long sizeId = id("SELECT id FROM product_sizes WHERE code = '" + variants[i][0] + "'");
            long colorId = id("SELECT id FROM product_colors WHERE code = '" + variants[i][1] + "'");
            sb.append(i > 0 ? "," : "").append("{\"sizeId\":").append(sizeId).append(",\"colorId\":").append(colorId)
                    .append(",\"quantityOnHand\":").append(variants[i][2]).append(",\"lowStockThreshold\":5,\"active\":true}");
        }
        return sb.append("]}").toString();
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON);
    }

    private MockMultipartHttpServletRequestBuilder asAdminMultipart(long productId, String filename, byte[] bytes) {
        return multipart("/api/admin/products/" + productId + "/images")
                .file(new MockMultipartFile("file", filename, "image/png", bytes))
                .header("Authorization", "Bearer " + admin);
    }

    private long id(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private static long idOf(MvcResult result) throws Exception {
        return Long.parseLong(jsonValue(result, "id"));
    }

    private static String token(MvcResult result) throws Exception {
        return jsonValue(result, "accessToken");
    }

    /** Minimal top-level field extraction to keep tests free of JSON library coupling. */
    private static String jsonValue(MvcResult result, String field) throws Exception {
        String json = result.getResponse().getContentAsString();
        String key = "\"" + field + "\":";
        int start = json.indexOf(key) + key.length();
        if (json.charAt(start) == '"') {
            return json.substring(start + 1, json.indexOf('"', start + 1));
        }
        int end = start;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(start, end);
    }
}
