package com.shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.shop.security.RateLimiter;
import com.shop.service.PasswordResetNotifier;
import com.shop.support.DatabaseCleaner;

import jakarta.servlet.http.Cookie;

/**
 * End-to-end authentication against MySQL: registration, login, JWT access,
 * refresh rotation and theft detection, roles, lockout, password reset, logout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIT {

    private static final String COOKIE = "tshirt_rt";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RateLimiter rateLimiter;
    @MockitoBean
    private PasswordResetNotifier resetNotifier;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbc);
        rateLimiter.reset();
    }

    @Test
    void registerThenUseAccessToken() throws Exception {
        MvcResult result = register("asha@example.com", "Secret123")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("asha@example.com"))
                .andExpect(jsonPath("$.user.roles[0]").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly(COOKIE, true))
                .andExpect(cookie().path(COOKIE, "/api/auth"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andReturn();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken(result)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.fullName").value("Asha Patil"));

        Integer stored = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'asha@example.com' "
                + "AND password_hash LIKE '$2a$12$%'", Integer.class);
        assertThat(stored).as("password stored as BCrypt cost 12").isEqualTo(1);
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"A\",\"email\":\"not-an-email\",\"phone\":\"12345\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        register("asha@example.com", "Secret123").andExpect(status().isCreated());
        register("ASHA@example.com", "Secret123")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void refreshRotatesAndReplayRevokesAllSessions() throws Exception {
        Cookie first = refreshCookie(register("asha@example.com", "Secret123").andReturn());

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie second = refreshCookie(refreshed);
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        // The successor is used, so a later replay of the first token cannot be a lost response.
        Cookie third = refreshCookie(mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isOk()).andReturn());

        mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
        mvc.perform(post("/api/auth/refresh").cookie(third))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void replayRightAfterALostRefreshResponseKeepsTheSession() throws Exception {
        Cookie first = refreshCookie(register("ravi@example.com", "Secret123").andReturn());
        // The browser sends the refresh but never stores the new cookie (page reloaded mid-request).
        Cookie lost = refreshCookie(mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isOk()).andReturn());

        Cookie next = refreshCookie(mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("ravi@example.com"))
                .andReturn());
        mvc.perform(post("/api/auth/refresh").cookie(lost)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(next)).andExpect(status().isOk());
    }

    @Test
    void refreshWithoutCookieMeansNoSession() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie(COOKIE, "forged-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void logoutRevokesRefreshTokenWithoutTouchingOtherSessions() throws Exception {
        Cookie deviceA = refreshCookie(register("asha@example.com", "Secret123").andReturn());
        Cookie deviceB = refreshCookie(login("asha@example.com", "Secret123").andReturn());

        mvc.perform(post("/api/auth/logout").cookie(deviceA))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(COOKIE, 0));

        mvc.perform(post("/api/auth/refresh").cookie(deviceA)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(deviceB)).andExpect(status().isOk());
    }

    @Test
    void customerCannotReachAdminApiButAdminCan() throws Exception {
        String customer = accessToken(register("asha@example.com", "Secret123").andReturn());
        String admin = accessToken(login(DatabaseCleaner.BOOTSTRAP_ADMIN_EMAIL, "TestAdmin123")
                .andExpect(jsonPath("$.user.roles").value(org.hamcrest.Matchers.hasItem("ROLE_ADMIN")))
                .andReturn());

        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        // Passes authorization; 404 because admin endpoints arrive in Phase 16.
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidAccessTokenIsRejected() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void fiveFailuresLockTheAccount() throws Exception {
        register("asha@example.com", "Secret123");
        for (int i = 0; i < 4; i++) {
            login("asha@example.com", "Wrong1234")
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
        login("asha@example.com", "Wrong1234").andExpect(status().isLocked());
        login("asha@example.com", "Secret123")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void passwordResetFlow() throws Exception {
        Cookie session = refreshCookie(register("asha@example.com", "Secret123").andReturn());

        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(resetNotifier).sendPasswordResetLink(any(), link.capture(), any());
        String token = link.getValue().substring(link.getValue().indexOf("token=") + 6);

        String body = "{\"token\":\"" + token + "\",\"newPassword\":\"NewSecret456\"}";
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));

        login("asha@example.com", "Secret123").andExpect(status().isUnauthorized());
        login("asha@example.com", "NewSecret456").andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").cookie(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRateLimitReturns429WithRetryAfter() throws Exception {
        // The test profile allows 1000 logins per minute; exhaust it quickly with unknown emails.
        for (int i = 0; i < 1000; i++) {
            rateLimiterCheck();
        }
        login("nobody@example.com", "Secret123")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    private void rateLimiterCheck() {
        rateLimiter.check("login:127.0.0.1", 1000, java.time.Duration.ofMinutes(1));
    }

    private org.springframework.test.web.servlet.ResultActions register(String email, String password)
            throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Asha Patil\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private static String accessToken(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        int start = json.indexOf("\"accessToken\":\"") + 15;
        return json.substring(start, json.indexOf('"', start));
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).isNotNull();
        return cookie;
    }
}
