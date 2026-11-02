package com.shop.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " not found: " + identifier);
    }
}
