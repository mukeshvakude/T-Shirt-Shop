package com.shop.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A signed-in customer's shopping cart (one per customer). Prices are never stored here. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** Coupon the customer applied; re-validated every time the cart is priced. */
    @Column(name = "coupon_code", length = 40)
    private String couponCode;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<CartItem> items = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;
}
