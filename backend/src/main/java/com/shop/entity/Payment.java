package com.shop.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One payment attempt for an order. Each Razorpay attempt has its own Razorpay order id; the
 * amount is always the order total set by the server.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, length = 20)
    private String provider = "RAZORPAY";

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "razorpay_order_id", length = 50, unique = true)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 50, unique = true)
    private String razorpayPaymentId;

    @Column(name = "razorpay_signature", length = 128)
    private String razorpaySignature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Order.PaymentStatus status = Order.PaymentStatus.CREATED;

    @Column(length = 30)
    private String method;

    @Column(name = "method_detail", length = 100)
    private String methodDetail;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "refunded_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_reason", length = 300)
    private String failureReason;

    /** Set when something needs a person to look at it (for example paid after the item sold out). */
    @Column(name = "needs_review", nullable = false)
    private boolean needsReview;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
