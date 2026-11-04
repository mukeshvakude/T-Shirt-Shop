package com.shop.controller.admin;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.dto.order.AdminOrderStatusRequest;
import com.shop.dto.order.OrderResponse;
import com.shop.notification.WhatsAppLinks;
import com.shop.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin: orders", description = "Order status changes and invoices (ROLE_ADMIN); full admin screens in Phase 16")
public class AdminOrderController {

    private static final String NUMBER = "^TS-[0-9]{8}-[0-9]{6}$";

    private final OrderService orders;
    private final WhatsAppLinks whatsApp;

    @GetMapping("/{orderNumber}/whatsapp")
    @Operation(summary = "One-click WhatsApp messages (wa.me links with the text typed) for the order's current state")
    public WhatsAppLinks.OrderLinks whatsapp(@PathVariable @Pattern(regexp = NUMBER) String orderNumber) {
        return whatsApp.forOrder(orderNumber);
    }

    @PatchMapping("/{orderNumber}/status")
    @Operation(summary = "Move an order to Processing, Shipped, Out for delivery, Delivered, or cancel it")
    public OrderResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = NUMBER) String orderNumber,
            @Valid @RequestBody AdminOrderStatusRequest request) {
        return orders.updateStatus(Long.valueOf(jwt.getSubject()), orderNumber, request);
    }

    @GetMapping("/{orderNumber}/invoice")
    @Operation(summary = "Invoice PDF of any confirmed order")
    public ResponseEntity<byte[]> invoice(@PathVariable @Pattern(regexp = NUMBER) String orderNumber) {
        OrderService.Invoice invoice = orders.invoice(null, true, orderNumber);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(invoice.fileName()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(invoice.pdf());
    }
}
