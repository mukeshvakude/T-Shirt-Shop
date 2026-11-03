package com.shop.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shop.service.impl.OrderExpiryService;

import lombok.RequiredArgsConstructor;

/** Every minute: release stock holds of unpaid orders whose window ended, and cancel stale unpaid orders. */
@Component
@RequiredArgsConstructor
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderExpiryService service;

    @Scheduled(fixedDelayString = "${app.jobs.order-expiry-delay-ms:60000}", initialDelayString = "${app.jobs.order-expiry-delay-ms:60000}")
    public void run() {
        for (Long id : service.ordersWithExpiredHolds()) {
            try {
                service.releaseExpiredHolds(id);
            } catch (RuntimeException ex) {
                log.error("Could not release stock holds of order {}", id, ex);
            }
        }
        for (Long id : service.unpaidOrdersToCancel()) {
            try {
                service.cancelUnpaid(id);
            } catch (RuntimeException ex) {
                log.error("Could not cancel unpaid order {}", id, ex);
            }
        }
    }
}
