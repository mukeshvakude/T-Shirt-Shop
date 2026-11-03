package com.shop.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line in a cart: a product variant (SKU), optionally printed with one of the customer's
 * saved designs. The same design may appear in several sizes.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inventory_id", nullable = false)
    private Inventory inventory;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "custom_design_id")
    private CustomDesign customDesign;

    @Column(nullable = false)
    private int quantity;

    public boolean isCustom() {
        return customDesign != null;
    }

    /** Same SKU and same design (or both plain). */
    public boolean sameLineAs(Inventory otherInventory, CustomDesign otherDesign) {
        Long design = customDesign == null ? null : customDesign.getId();
        Long other = otherDesign == null ? null : otherDesign.getId();
        return inventory.getId().equals(otherInventory.getId()) && java.util.Objects.equals(design, other);
    }
}
