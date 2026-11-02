package com.shop.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for expected business errors. The {@code code} is a stable,
 * machine-readable identifier the Angular app can switch on.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
