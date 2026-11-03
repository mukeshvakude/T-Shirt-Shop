package com.shop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shop.entity.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    Optional<Payment> findTopByOrderIdOrderByAttemptNoDesc(Long orderId);

    List<Payment> findByOrderIdOrderByAttemptNoAsc(Long orderId);

    /** Unconfirmed attempts in a time window, oldest first (reconciliation with Razorpay). */
    @org.springframework.data.jpa.repository.Query("""
            select p.id from Payment p
            where p.status in :statuses and p.razorpayOrderId is not null and p.createdAt >= :from and p.createdAt <= :to
            order by p.id""")
    List<Long> findForReconciliation(@org.springframework.data.repository.query.Param("statuses") java.util.Collection<com.shop.entity.Order.PaymentStatus> statuses,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from,
            @org.springframework.data.repository.query.Param("to") java.time.Instant to, org.springframework.data.domain.Pageable page);

    /** Paid attempts of cancelled orders that still need their refund (an earlier refund call failed). */
    @org.springframework.data.jpa.repository.Query("""
            select p.id from Payment p, Order o
            where o.id = p.orderId and p.status = com.shop.entity.Order.PaymentStatus.SUCCESS
              and o.orderStatus = com.shop.entity.Order.Status.CANCELLED and p.refundedAmount = 0""")
    List<Long> findRefundsToRetry();
}
