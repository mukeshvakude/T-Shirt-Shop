package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

import com.shop.security.RateLimiter;
import com.shop.support.DatabaseCleaner;
import com.shop.support.TestImages;

/**
 * Custom T-Shirt designer (Phase 9) against MySQL: options, artwork upload,
 * saving with server-side validation, ownership, admin access and ZIP download.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DesignIT {

    private static final String PREVIEW = "data:image/png;base64," + Base64.getEncoder().encodeToString(TestImages.png(300, 375));

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;

    private String customer;

    @BeforeEach
    void setUp() throws Exception {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
        customer = register("kavya@example.com");
    }

    @Test
    void optionsDescribeTheDesigner() throws Exception {
        mvc.perform(get("/api/designer/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tshirtTypes[*].code", org.hamcrest.Matchers.contains("ROUND_NECK", "V_NECK", "POLO", "OVERSIZED")))
                .andExpect(jsonPath("$.tshirtTypes[0].basePrice").value(299.00))
                .andExpect(jsonPath("$.tshirtTypes[0].mockup").value("round-neck"))
                .andExpect(jsonPath("$.colors", hasSize(8)))
                .andExpect(jsonPath("$.sizes[*].code", org.hamcrest.Matchers.contains("S", "M", "L", "XL", "XXL")))
                .andExpect(jsonPath("$.placements", hasSize(5)))
                .andExpect(jsonPath("$.placements[?(@.code=='FULL_FRONT')].excludes[*]", org.hamcrest.Matchers.containsInAnyOrder("FRONT", "LEFT_CHEST")))
                .andExpect(jsonPath("$.fonts", hasSize(8)))
                .andExpect(jsonPath("$.availability", hasSize(160)))
                .andExpect(jsonPath("$.limits.maxUploadBytes").value(5242880));
    }

    @Test
    void artworkUploadIsValidatedAndPrivate() throws Exception {
        mvc.perform(multipart("/api/designs/assets").file(png("logo.png"))).andExpect(status().isUnauthorized());

        MvcResult ok = mvc.perform(multipart("/api/designs/assets").file(png("my logo (final).png")).header("Authorization", "Bearer " + customer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url", startsWith("/media/designs/")))
                .andExpect(jsonPath("$.originalFilename").value("my_logo_final_.png"))
                .andExpect(jsonPath("$.width").value(120))
                .andReturn();
        mvc.perform(get(field(ok, "url")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));

        mvc.perform(multipart("/api/designs/assets")
                        .file(new MockMultipartFile("file", "evil.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                        .header("Authorization", "Bearer " + customer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void savesOpensListsAndProtectsDesigns() throws Exception {
        String asset = upload(customer);
        MvcResult created = mvc.perform(as(post("/api/designs"), customer).content(design(asset, "FRONT", "Anton", "MAROON")
                        .replace("\"MAROON\"", "\"BLACK\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", startsWith("DSN-")))
                .andExpect(jsonPath("$.placements[0]").value("FRONT"))
                .andExpect(jsonPath("$.previewFrontUrl", startsWith("/media/previews/")))
                .andExpect(jsonPath("$.previewBackUrl").doesNotExist())
                .andExpect(jsonPath("$.front.objects", hasSize(2)))
                .andExpect(jsonPath("$.assets", hasSize(1)))
                .andExpect(jsonPath("$.productSlug").value("custom-round-neck-t-shirt"))
                .andReturn();
        String code = field(created, "code");

        mvc.perform(as(get("/api/designs"), customer)).andExpect(jsonPath("$[0].code").value(code));
        mvc.perform(as(get("/api/designs/" + code), customer)).andExpect(status().isOk());

        String intruder = register("intruder@example.com");
        mvc.perform(as(get("/api/designs/" + code), intruder)).andExpect(status().isNotFound());
        mvc.perform(as(put("/api/designs/" + code), intruder).content(design(asset, "FRONT", "Anton", "BLACK")))
                .andExpect(status().isNotFound());
        mvc.perform(as(delete("/api/designs/" + code), intruder)).andExpect(status().isNotFound());

        // Update: move to the back and change colour
        String backDesign = design(asset, "BACK", "Lobster", "WHITE").replace("\"front\"", "\"back\"").replace("\"side\":\"FRONT\"", "\"side\":\"BACK\"");
        mvc.perform(as(put("/api/designs/" + code), customer).content(backDesign))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placements[0]").value("BACK"))
                .andExpect(jsonPath("$.colorCode").value("WHITE"))
                .andExpect(jsonPath("$.previewBackUrl", startsWith("/media/previews/")))
                .andExpect(jsonPath("$.front").doesNotExist());

        // Locked (ordered) designs cannot change
        jdbc.update("UPDATE custom_designs SET locked = TRUE WHERE design_code = ?", code);
        mvc.perform(as(put("/api/designs/" + code), customer).content(backDesign))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DESIGN_LOCKED"));
        mvc.perform(as(delete("/api/designs/" + code), customer)).andExpect(status().isUnprocessableContent());
        jdbc.update("UPDATE custom_designs SET locked = FALSE WHERE design_code = ?", code);
        mvc.perform(as(delete("/api/designs/" + code), customer)).andExpect(status().isNoContent());
        mvc.perform(as(get("/api/designs"), customer)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void serverRejectsInvalidDesigns() throws Exception {
        String asset = upload(customer);
        String foreignAsset = upload(register("other@example.com"));
        String good = design(asset, "FRONT", "Anton", "BLACK");

        expectError(good.replace("/media/designs/", "https://evil.example/"), "INVALID_DESIGN");
        expectError(design(foreignAsset, "FRONT", "Anton", "BLACK"), "INVALID_DESIGN");
        expectError(good.replace("\"placements\":[\"FRONT\"]", "\"placements\":[\"FRONT\",\"FULL_FRONT\"]"), "PLACEMENT_CONFLICT");
        expectError(good.replace("\"placements\":[\"FRONT\"]", "\"placements\":[\"BACK\"]"), "PLACEMENT_REQUIRED");
        expectError(good.replace("\"placements\":[\"FRONT\"]", "\"placements\":[\"FRONT\",\"BACK\"]"), "EMPTY_PLACEMENT");
        expectError(good.replace("\"font\":\"Anton\"", "\"font\":\"Comic Sans\""), "UNKNOWN_FONT");
        expectError(good.replace("\"colorCode\":\"BLACK\"", "\"colorCode\":\"MAROON\""), "VARIANT_UNAVAILABLE");
        expectError(good.replace("\"tshirtType\":\"ROUND_NECK\"", "\"tshirtType\":\"HOODIE\""), "UNKNOWN_TSHIRT_TYPE");
        expectError(good.replace(PREVIEW, "data:image/png;base64,bm90IGFuIGltYWdl"), "IMAGE_TYPE_NOT_ALLOWED");
        expectError("{\"tshirtType\":\"ROUND_NECK\",\"colorCode\":\"BLACK\",\"sizeCode\":\"M\",\"placements\":[\"FRONT\"],"
                + "\"front\":{\"objects\":[]},\"textItems\":[],\"assetIds\":[],\"premiumPrint\":false,\"previewFront\":\"" + PREVIEW + "\"}", "EMPTY_DESIGN");
    }

    @Test
    void adminSeesAllDesignsAndDownloadsZip() throws Exception {
        String asset = upload(customer);
        String code = field(mvc.perform(as(post("/api/designs"), customer).content(design(asset, "FRONT", "Anton", "BLACK")))
                .andExpect(status().isCreated()).andReturn(), "code");
        String admin = login("admin@test.local", "TestAdmin123");

        mvc.perform(as(get("/api/admin/designs"), customer)).andExpect(status().isForbidden());
        mvc.perform(as(get("/api/admin/designs"), admin))
                .andExpect(jsonPath("$.content[0].code").value(code))
                .andExpect(jsonPath("$.content[0].customerEmail").value("kavya@example.com"));
        mvc.perform(as(get("/api/designs/" + code), admin)).andExpect(status().isOk());

        MvcResult zip = mvc.perform(as(get("/api/admin/designs/" + code + "/download"), admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(code + ".zip")))
                .andReturn();
        // StreamingResponseBody completes asynchronously.
        MvcResult done = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(zip)).andReturn();
        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(done.getResponse().getContentAsByteArray()))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) entries.add(entry.getName());
        }
        assertThat(entries).contains("design.json", "preview-front.png").anyMatch(e -> e.startsWith("artwork/"));
        mvc.perform(as(get("/api/admin/designs/DSN-AAAAAAAA/download"), admin)).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- helpers

    private void expectError(String body, String code) throws Exception {
        mvc.perform(as(post("/api/designs"), customer).content(body))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(code));
    }

    private static String design(String assetUrlAndId, String placement, String font, String color) {
        String[] parts = assetUrlAndId.split("\\|");
        String url = parts[0];
        String id = parts[1];
        return "{\"name\":\"Birthday tee\",\"tshirtType\":\"ROUND_NECK\",\"colorCode\":\"" + color + "\",\"sizeCode\":\"M\","
                + "\"placements\":[\"" + placement + "\"],"
                + "\"front\":{\"version\":\"7.4.0\",\"objects\":[{\"type\":\"Image\",\"src\":\"" + url + "\",\"left\":100,\"top\":80},"
                + "{\"type\":\"IText\",\"text\":\"Happy 30th\",\"fontFamily\":\"" + font + "\",\"fill\":\"#ffffff\"}]},"
                + "\"textItems\":[{\"side\":\"FRONT\",\"text\":\"Happy 30th\",\"font\":\"" + font + "\",\"size\":40,\"color\":\"#FFFFFF\"}],"
                + "\"assetIds\":[" + id + "],\"premiumPrint\":false,"
                + "\"previewFront\":\"" + PREVIEW + "\",\"previewBack\":\"" + PREVIEW + "\"}";
    }

    private String upload(String token) throws Exception {
        MvcResult r = mvc.perform(multipart("/api/designs/assets").file(png("art.png")).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return field(r, "url") + "|" + field(r, "id");
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("file", name, "image/png", TestImages.png(120, 90));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String register(String email) throws Exception {
        return field(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Kavya Rao\",\"email\":\"" + email + "\",\"password\":\"Secret123\"}"))
                .andExpect(status().isCreated()).andReturn(), "accessToken");
    }

    private String login(String email, String password) throws Exception {
        return field(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")).andReturn(), "accessToken");
    }

    private static String field(MvcResult result, String name) throws Exception {
        String json = result.getResponse().getContentAsString();
        String key = "\"" + name + "\":";
        int start = json.indexOf(key) + key.length();
        if (json.charAt(start) == '"') {
            return json.substring(start + 1, json.indexOf('"', start + 1));
        }
        int end = start;
        while (",}".indexOf(json.charAt(end)) < 0) end++;
        return json.substring(start, end);
    }
}
