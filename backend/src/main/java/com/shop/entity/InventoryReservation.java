package com.shop.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Stock held for an order. ACTIVE holds count in inventory.quantity_reserved until they are
 * COMMITTED (paid or COD), RELEASED (cancelled) or EXPIRED (payment window passed).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "inventory_reservations")
public class InventoryReservation extends BaseEntity {

    public enum Status { ACTIVE, COMMITTED, RELEASED, EXPIRED }

    @Column(name = "inventory_id", nullable = false)
    private Long inventoryId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
