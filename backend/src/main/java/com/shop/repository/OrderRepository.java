package com.shop.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.entity.Order;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderNumber(String orderNumber);

    @Query("select distinct o from Order o left join fetch o.items i left join fetch i.customDesign where o.orderNumber = :number")
    Optional<Order> findByNumberWithItems(@Param("number") String number);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    /** Unpaid pending orders placed before the cut-off. */
    @Query("""
            select o.id from Order o
            where o.orderStatus = com.shop.entity.Order.Status.PENDING
              and o.paymentStatus <> com.shop.entity.Order.PaymentStatus.SUCCESS
              and o.placedAt <= :cutoff""")
    List<Long> findUnpaidPlacedBefore(@Param("cutoff") Instant cutoff);

    /** Other open orders that print this design (used before unlocking it). */
    @Query(value = """
            select count(*) from order_items oi join orders o on o.id = oi.order_id
            where oi.custom_design_id = :designId and o.id <> :orderId and o.order_status <> 'CANCELLED'""", nativeQuery = true)
    long countOtherOpenOrdersWithDesign(@Param("designId") Long designId, @Param("orderId") Long orderId);
}
