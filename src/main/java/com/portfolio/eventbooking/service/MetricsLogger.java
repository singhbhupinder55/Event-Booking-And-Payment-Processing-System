package com.portfolio.eventbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Logs the current metrics snapshot on a fixed interval. This is the
 * "even just logged counters is fine" piece from the brief — a real
 * production system would scrape /api/metrics or a Micrometer/Prometheus
 * endpoint instead of grepping logs, but for a portfolio project this is
 * enough to demonstrate the observability habit without over-building.
 */
@Component
public class MetricsLogger {

    private static final Logger log = LoggerFactory.getLogger(MetricsLogger.class);

    private final MetricsService metricsService;

    public MetricsLogger(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void logSnapshot() {
        MetricsService.Snapshot snapshot = metricsService.snapshot();
        log.info(
            "Metrics snapshot: bookingAttempts={} bookingSuccesses={} bookingSoldOutRejections={} " +
            "bookingSuccessRate={} paymentIntentsCreated={} paymentsSucceeded={} paymentsFailed={} " +
            "paymentFailureRate={} refunds={} reservationsExpired={}",
            snapshot.bookingAttempts(), snapshot.bookingSuccesses(), snapshot.bookingSoldOutRejections(),
            String.format("%.2f%%", snapshot.bookingSuccessRate() * 100),
            snapshot.paymentIntentsCreated(), snapshot.paymentsSucceeded(), snapshot.paymentsFailed(),
            String.format("%.2f%%", snapshot.paymentFailureRate() * 100),
            snapshot.refunds(), snapshot.reservationsExpired());
    }
}
