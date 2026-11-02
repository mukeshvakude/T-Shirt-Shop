package com.shop.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Caller details stored with refresh tokens and used for rate limiting.
 * The IP honours X-Forwarded-For only through Spring forwarded-header handling,
 * so in production the reverse proxy must overwrite that header.
 */
public record ClientInfo(String ipAddress, String userAgent) {

    private static final int MAX_USER_AGENT = 255;
    private static final int MAX_IP = 45;

    public static ClientInfo from(HttpServletRequest request) {
        return new ClientInfo(truncate(request.getRemoteAddr(), MAX_IP),
                truncate(request.getHeader("User-Agent"), MAX_USER_AGENT));
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
