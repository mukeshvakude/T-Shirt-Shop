package com.shop.dto.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.shop.dto.order.OrderResponse;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Read models for the admin portal (Phase 16). */
public final class AdminViews {

    private AdminViews() {
    }

    // ---------------------------------------------------------------- dashboard

    public record Dashboard(
            Period today,
            Period last7Days,
            Period last30Days,
            int toShip,
            int awaitingPayment,
            int paymentsNeedingReview,
            int lowStockSkus,
            int newMessages,
            int newCustomers30Days,
            BigDecimal refunded30Days,
            List<StatusCount> ordersByStatus,
            List<DaySales> salesByDay,
            List<TopProduct> topProducts,
            List<OrderRow> recentOrders) {
    }

    /** Sales are orders that are not pending payment and not cancelled. */
    public record Period(int orders, BigDecimal sales, BigDecimal averageOrder) {
    }

    public record StatusCount(String status, int count) {
    }

    public record DaySales(LocalDate day, int orders, BigDecimal sales) {
    }

    public record TopProduct(Long productId, String name, int quantity, BigDecimal sales) {
    }

    // ---------------------------------------------------------------- orders

    public record OrderRow(String orderNumber, Instant placedAt, String customerName, String customerEmail, int itemCount,
            BigDecimal total, String status, String paymentMethod, String paymentStatus, boolean needsReview) {
    }

    public record OrderDetail(OrderResponse order, Long customerId, List<PaymentRow> payments, List<String> nextStatuses) {
    }

    // ---------------------------------------------------------------- payments

    public record PaymentRow(Long id, String orderNumber, int attemptNo, String provider, String status, String razorpayOrderId,
            String razorpayPaymentId, String method, String methodDetail, BigDecimal amount, BigDecimal refundedAmount,
            boolean needsReview, String failureReason, Instant createdAt, Instant paidAt) {
    }

    public record PaymentDetail(PaymentRow payment, List<Transaction> transactions) {
    }

    public record Transaction(Instant at, String type, String status, String source, String reference, BigDecimal amount,
            String details) {
    }

    public record RefundRequest(@NotBlank @Size(max = 200) String reason) {
    }

    // ---------------------------------------------------------------- customers

    public record CustomerRow(Long id, String fullName, String email, String phone, boolean enabled, Instant joinedAt,
            Instant lastLoginAt, int orders, BigDecimal spent) {
    }

    public record CustomerDetail(CustomerRow customer, int addresses, int designs, List<OrderRow> orders) {
    }

    public record CustomerStatusRequest(@NotNull Boolean enabled) {
    }

    // ---------------------------------------------------------------- messages

    public record MessageRow(Long id, String name, String email, String phone, String subject, String message, String status,
            Instant createdAt) {
    }

    public record MessageStatusRequest(@NotBlank @Pattern(regexp = "NEW|READ|REPLIED|CLOSED") String status) {
    }
}
