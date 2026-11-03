package com.shop.entity;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Audit log of everything that happened to a payment (gateway calls, verification, failures). */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    /** ORDER_CREATED, VERIFY, AUTHORIZED, CAPTURED, FAILED, REFUND_INITIATED, REFUNDED, COD_COLLECTED */
    @Column(nullable = false, length = 30)
    private String type;

    @Column(name = "gateway_reference", length = 80)
    private String gatewayReference;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 30)
    private String status;

    /** CLIENT_VERIFY, WEBHOOK, RECONCILE, ADMIN, SYSTEM */
    @Column(nullable = false, length = 20)
    private String source;

    /** Gateway response or client report, without secrets. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_response", columnDefinition = "json")
    private String rawResponse;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
