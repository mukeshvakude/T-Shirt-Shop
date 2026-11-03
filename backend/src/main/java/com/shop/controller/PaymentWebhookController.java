package com.shop.controller;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.service.impl.PaymentWebhookService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/** Receives Razorpay webhooks. Public, but every delivery must carry a valid signature. */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@SecurityRequirements
@Tag(name = "Payments")
public class PaymentWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PaymentWebhookController.class);

    private final PaymentWebhookService webhooks;

    @PostMapping("/webhook")
    @Operation(summary = "Razorpay webhook (X-Razorpay-Signature over the raw body; idempotent by X-Razorpay-Event-Id)")
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        webhooks.verifySignature(body, signature);
        Optional<Long> pending = webhooks.record(body, eventId);
        if (pending.isEmpty()) {
            return ResponseEntity.ok().build(); // already handled
        }
        try {
            webhooks.process(pending.get());
            return ResponseEntity.ok().build();
        } catch (RuntimeException ex) {
            log.error("Webhook event {} failed; Razorpay will retry", eventId, ex);
            webhooks.markFailed(pending.get(), ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
