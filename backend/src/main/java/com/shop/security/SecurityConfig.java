package com.shop.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.shop.config.AppProperties;

/**
 * Stateless API security. Requests authenticate with "Authorization: Bearer <JWT>".
 * Roles come from the token "roles" claim (ROLE_CUSTOMER, ROLE_ADMIN).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Read-only endpoints anyone may call. */
    static final String[] PUBLIC_GET = {
            "/api/categories/**",
            "/api/reference/**",
            "/api/products/**",
            "/media/**",
            "/api/home",
            "/api/offers",
            "/api/reviews/**",
            "/api/designer/**",
    };

    /** Auth endpoints that must work without an access token. */
    static final String[] PUBLIC_AUTH = {
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/newsletter",
            "/api/contact",
    };

    static final String[] API_DOCS = {
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
    };

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, ProblemDetailSecurityHandler problemHandler,
            JwtAuthenticationConverter jwtAuthenticationConverter, AppProperties properties) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000)))
                .oauth2ResourceServer(o -> o
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll();
                    auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    auth.requestMatchers("/error").permitAll();
                    if (properties.apiDocsEnabled()) {
                        auth.requestMatchers(API_DOCS).permitAll();
                    }
                    auth.requestMatchers(HttpMethod.POST, PUBLIC_AUTH).permitAll();
                    auth.requestMatchers(HttpMethod.GET, PUBLIC_GET).permitAll();
                    auth.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    auth.anyRequest().authenticated();
                });
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
