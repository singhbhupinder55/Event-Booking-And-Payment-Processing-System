package com.portfolio.eventbooking.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MetricsServiceTest {

    @Test
    void freshServiceReportsAllZeros() {
        MetricsService service = new MetricsService();
        MetricsService.Snapshot snapshot = service.snapshot();

        assertThat(snapshot.bookingAttempts()).isZero();
        assertThat(snapshot.bookingSuccessRate()).isZero();
        assertThat(snapshot.paymentFailureRate()).isZero();
    }

    @Test
    void bookingSuccessRateComputesCorrectly() {
        MetricsService service = new MetricsService();
        for (int i = 0; i < 10; i++) {
            service.recordBookingAttempt();
        }
        for (int i = 0; i < 7; i++) {
            service.recordBookingSuccess();
        }
        for (int i = 0; i < 3; i++) {
            service.recordBookingSoldOutRejection();
        }

        MetricsService.Snapshot snapshot = service.snapshot();

        assertThat(snapshot.bookingAttempts()).isEqualTo(10);
        assertThat(snapshot.bookingSuccesses()).isEqualTo(7);
        assertThat(snapshot.bookingSoldOutRejections()).isEqualTo(3);
        assertThat(snapshot.bookingSuccessRate()).isCloseTo(0.7, within(0.0001));
    }

    @Test
    void paymentFailureRateComputesCorrectly() {
        MetricsService service = new MetricsService();
        for (int i = 0; i < 8; i++) {
            service.recordPaymentSucceeded();
        }
        for (int i = 0; i < 2; i++) {
            service.recordPaymentFailed();
        }

        MetricsService.Snapshot snapshot = service.snapshot();

        assertThat(snapshot.paymentFailureRate()).isCloseTo(0.2, within(0.0001));
    }

    @Test
    void countersAreThreadSafeUnderConcurrentIncrement() throws InterruptedException {
        // Mirrors the actual production scenario this matters for: the
        // concurrency test fires 100 threads at the booking endpoint
        // simultaneously, and recordBookingAttempt()/recordBookingSuccess()
        // get called from all of them concurrently.
        MetricsService service = new MetricsService();
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    service.recordBookingAttempt();
                    service.recordBookingSuccess();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        MetricsService.Snapshot snapshot = service.snapshot();
        assertThat(snapshot.bookingAttempts()).isEqualTo(threadCount);
        assertThat(snapshot.bookingSuccesses()).isEqualTo(threadCount);
    }
}
