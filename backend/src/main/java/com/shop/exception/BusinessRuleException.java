package com.shop.exception;

import org.springframework.http.HttpStatus;

/**
 * A request that is well-formed but breaks a business rule
 * (for example: out of stock, coupon expired, order not cancellable).
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
