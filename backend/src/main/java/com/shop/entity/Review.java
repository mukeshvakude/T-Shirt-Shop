package com.shop.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A product review. Only approved reviews are shown publicly. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "reviews")
public class Review extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Verified purchase reference (order lines arrive in Phase 15). */
    @Column(name = "order_id")
    private Long orderId;

    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 120)
    private String title;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false)
    private boolean approved;
}
