package com.shop.exception;

import org.springframework.http.HttpStatus;

/** The payment gateway could not be reached or refused a request (502 to the client). */
public class PaymentGatewayException extends ApiException {

    public PaymentGatewayException(String message) {
        super(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", message);
    }
}
