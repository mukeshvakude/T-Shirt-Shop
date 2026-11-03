package com.shop.controller.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.PageResponse;
import com.shop.dto.admin.AdminViews;
import com.shop.service.impl.AdminConsoleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin: console", description = "Dashboard, orders, payments, refunds, customers and messages (ROLE_ADMIN)")
public class AdminConsoleController {

    private static final String ORDER = "^TS-[0-9]{8}-[0-9]{6}$";
    private static final String ORDER_STATUS = "^(PENDING|CONFIRMED|PROCESSING|SHIPPED|OUT_FOR_DELIVERY|DELIVERED|CANCELLED)?$";
    private static final String PAYMENT_STATUS = "^(CREATED|PENDING|SUCCESS|FAILED|CANCELLED|REFUNDED)?$";

    private final AdminConsoleService console;

    @GetMapping("/dashboard")
    @Operation(summary = "Sales, order counts, work to do, top products and recent orders")
    public AdminViews.Dashboard dashboard() {
        return console.dashboard();
    }

    @GetMapping("/orders")
    @Operation(summary = "All orders, newest first; filter by status, search number, name, email or phone")
    public PageResponse<AdminViews.OrderRow> orders(@RequestParam(required = false) @Pattern(regexp = ORDER_STATUS) String status,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "false") boolean needsReview,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return console.orderRows(status, q, needsReview, page, size);
    }

    @GetMapping("/orders/{orderNumber}")
    @Operation(summary = "Order with payments and the statuses it can move to")
    public AdminViews.OrderDetail order(@PathVariable @Pattern(regexp = ORDER) String orderNumber) {
        return console.orderDetail(orderNumber);
    }

    @GetMapping("/payments")
    @Operation(summary = "Payment attempts, newest first")
    public PageResponse<AdminViews.PaymentRow> payments(@RequestParam(required = false) @Pattern(regexp = PAYMENT_STATUS) String status,
            @RequestParam(defaultValue = "false") boolean needsReview, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return console.paymentList(status, needsReview, q, page, size);
    }

    @GetMapping("/payments/{id}")
    @Operation(summary = "Payment with its full gateway and audit log")
    public AdminViews.PaymentDetail payment(@PathVariable Long id) {
        return console.paymentDetail(id);
    }

    @PostMapping("/payments/{id}/refund")
    @Operation(summary = "Refund a paid online payment in full")
    public AdminViews.PaymentDetail refund(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody AdminViews.RefundRequest request) {
        return console.refund(id, request.reason(), Long.valueOf(jwt.getSubject()));
    }

    @PostMapping("/payments/{id}/reviewed")
    @Operation(summary = "Clear the review flag after checking a payment")
    public AdminViews.PaymentDetail reviewed(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return console.markReviewed(id, Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/customers")
    @Operation(summary = "Customers with order count and amount spent")
    public PageResponse<AdminViews.CustomerRow> customers(@RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return console.customers(q, page, size);
    }

    @GetMapping("/customers/{id}")
    @Operation(summary = "Customer with recent orders")
    public AdminViews.CustomerDetail customer(@PathVariable Long id) {
        return console.customer(id);
    }

    @PatchMapping("/customers/{id}")
    @Operation(summary = "Enable or disable a customer account (disabling signs them out)")
    public AdminViews.CustomerDetail setEnabled(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @Valid @RequestBody AdminViews.CustomerStatusRequest request) {
        return console.setEnabled(id, request.enabled(), Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/messages")
    @Operation(summary = "Contact form messages, newest first")
    public PageResponse<AdminViews.MessageRow> messages(@RequestParam(required = false) @Pattern(regexp = "^(NEW|READ|REPLIED|CLOSED)?$") String status,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return console.messages(status, page, size);
    }

    @PatchMapping("/messages/{id}")
    @Operation(summary = "Mark a message read, replied or closed")
    public ResponseEntity<Void> message(@PathVariable Long id, @Valid @RequestBody AdminViews.MessageStatusRequest request) {
        console.setMessageStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }
}
