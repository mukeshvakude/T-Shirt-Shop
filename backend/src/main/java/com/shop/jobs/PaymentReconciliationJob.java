package com.shop.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shop.config.AppProperties;
import com.shop.service.impl.PaymentReconciliationService;

import lombok.RequiredArgsConstructor;

/** Every 5 minutes: confirm payments Razorpay received but the shop never heard about, and retry failed refunds. */
@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentReconciliationService service;
    private final AppProperties properties;

    @Scheduled(fixedDelayString = "${app.jobs.payment-reconcile-delay-ms:300000}", initialDelayString = "${app.jobs.payment-reconcile-initial-ms:120000}")
    public void run() {
        if (properties.payments() == null || !properties.payments().razorpayConfigured()) {
            return;
        }
        for (Long id : service.attemptsToCheck()) {
            try {
                service.reconcile(id);
            } catch (RuntimeException ex) {
                log.warn("Could not reconcile payment attempt {}: {}", id, ex.getMessage());
            }
        }
        for (Long id : service.refundsToRetry()) {
            try {
                service.retryRefund(id);
            } catch (RuntimeException ex) {
                log.warn("Could not retry refund for payment {}: {}", id, ex.getMessage());
            }
        }
    }
}
