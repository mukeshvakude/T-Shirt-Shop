package com.shop.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.InventoryReservation;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, InventoryReservation.Status status);

    /** Orders that have ACTIVE holds past their expiry. */
    @Query("""
            select distinct r.orderId from InventoryReservation r
            where r.status = com.shop.entity.InventoryReservation.Status.ACTIVE and r.expiresAt <= :now""")
    List<Long> findOrderIdsWithExpiredHolds(@Param("now") Instant now);
}
