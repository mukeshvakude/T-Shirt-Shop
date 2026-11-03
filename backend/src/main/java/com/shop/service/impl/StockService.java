package com.shop.service.impl;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shop.entity.Inventory;
import com.shop.entity.InventoryReservation;
import com.shop.exception.ConflictException;
import com.shop.exception.ResourceNotFoundException;
import com.shop.repository.InventoryRepository;
import com.shop.repository.InventoryReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * Stock holds for orders (architecture section 7). Rows are locked with SELECT ... FOR UPDATE in
 * ascending SKU id order, so concurrent checkouts cannot oversell or deadlock each other.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class StockService {

    private final InventoryRepository inventory;
    private final InventoryReservationRepository reservations;

    /**
     * Holds stock for an order. With {@code commitNow} (Cash on Delivery) the stock is taken
     * straight away; otherwise it is reserved until {@code expiresAt}.
     *
     * @param quantities inventory id to quantity
     * @throws ConflictException OUT_OF_STOCK when any SKU no longer has enough stock (nothing is held)
     */
    public void hold(Long orderId, Map<Long, Integer> quantities, Instant expiresAt, boolean commitNow, Instant now) {
        for (Map.Entry<Long, Integer> e : new TreeMap<>(quantities).entrySet()) {
            Inventory inv = inventory.findByIdForUpdate(e.getKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Stock item", e.getKey()));
            int qty = e.getValue();
            if (!inv.isActive() || inv.getAvailable() < qty) {
                String what = inv.getProduct().getName() + " (" + inv.getSize().getCode() + ", " + inv.getColor().getName() + ")";
                throw new ConflictException("OUT_OF_STOCK", inv.getAvailable() == 0 || !inv.isActive()
                        ? what + " just sold out. Please update your bag."
                        : "Only " + inv.getAvailable() + " left of " + what + ". Please update your bag.");
            }
            InventoryReservation r = new InventoryReservation();
            r.setInventoryId(inv.getId());
            r.setOrderId(orderId);
            r.setQuantity(qty);
            if (commitNow) {
                inv.setQuantityOnHand(inv.getQuantityOnHand() - qty);
                r.setStatus(InventoryReservation.Status.COMMITTED);
                r.setExpiresAt(now);
                r.setResolvedAt(now);
            } else {
                inv.setQuantityReserved(inv.getQuantityReserved() + qty);
                r.setStatus(InventoryReservation.Status.ACTIVE);
                r.setExpiresAt(expiresAt);
            }
            reservations.save(r);
        }
    }

    /** Turns active holds into sold stock (payment confirmed). Returns the number of holds committed. */
    public int commit(Long orderId, Instant now) {
        return resolve(orderId, InventoryReservation.Status.COMMITTED, now);
    }

    /** Gives active holds back to the shop (expired or cancelled). Returns the number of holds released. */
    public int release(Long orderId, InventoryReservation.Status outcome, Instant now) {
        if (outcome != InventoryReservation.Status.EXPIRED && outcome != InventoryReservation.Status.RELEASED) {
            throw new IllegalArgumentException("Release outcome must be EXPIRED or RELEASED");
        }
        return resolve(orderId, outcome, now);
    }

    private int resolve(Long orderId, InventoryReservation.Status outcome, Instant now) {
        List<InventoryReservation> active = reservations.findByOrderIdAndStatus(orderId, InventoryReservation.Status.ACTIVE);
        active.sort((a, b) -> Long.compare(a.getInventoryId(), b.getInventoryId()));
        for (InventoryReservation r : active) {
            Inventory inv = inventory.findByIdForUpdate(r.getInventoryId()).orElseThrow();
            inv.setQuantityReserved(Math.max(0, inv.getQuantityReserved() - r.getQuantity()));
            if (outcome == InventoryReservation.Status.COMMITTED) {
                inv.setQuantityOnHand(Math.max(0, inv.getQuantityOnHand() - r.getQuantity()));
            }
            r.setStatus(outcome);
            r.setResolvedAt(now);
        }
        return active.size();
    }
}
