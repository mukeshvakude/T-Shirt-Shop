package com.shop.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.PageResponse;
import com.shop.dto.order.CancelOrderRequest;
import com.shop.dto.order.OrderResponse;
import com.shop.dto.order.OrderSummary;
import com.shop.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Orders", description = "The customer's own orders: history, details, tracking, cancellation, invoices")
public class OrderController {

    static final String NUMBER = "^TS-[0-9]{8}-[0-9]{6}$";

    private final OrderService orders;

    @GetMapping
    @Operation(summary = "My orders, newest first")
    public PageResponse<OrderSummary> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return orders.list(Long.valueOf(jwt.getSubject()), page, size);
    }

    @GetMapping("/{orderNumber}")
    @Operation(summary = "One of my orders with tracking and status history")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = NUMBER) String orderNumber) {
        return orders.get(Long.valueOf(jwt.getSubject()), orderNumber);
    }

    @PostMapping("/{orderNumber}/cancel")
    @Operation(summary = "Cancel my order before it is being prepared (paid online orders are refunded in full)")
    public OrderResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = NUMBER) String orderNumber,
            @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orders.cancel(Long.valueOf(jwt.getSubject()), orderNumber, request == null ? null : request.reason());
    }

    @GetMapping("/{orderNumber}/invoice")
    @Operation(summary = "Invoice PDF of my confirmed order")
    public ResponseEntity<byte[]> invoice(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = NUMBER) String orderNumber) {
        return pdf(orders.invoice(Long.valueOf(jwt.getSubject()), false, orderNumber));
    }

    static ResponseEntity<byte[]> pdf(OrderService.Invoice invoice) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(invoice.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(invoice.pdf());
    }
}
