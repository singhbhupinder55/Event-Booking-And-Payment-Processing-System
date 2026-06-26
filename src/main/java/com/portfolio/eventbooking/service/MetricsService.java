package com.portfolio.eventbooking.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Per the brief: "Basic metrics (booking success rate, payment failure
 * rate) — even just logged counters is fine for a portfolio project;
 * doesn't need a full Prometheus setup unless you want to go further."
 *
 * Deliberately simple: in-memory AtomicLong counters, no persistence, no
 * time-windowing, reset on app restart. This is the appropriate scope for
 * a portfolio project — a real production system would back this with
 * Micrometer + Prometheus (which Spring Boot Actuator already pulls in as
 * a dependency here, so that upgrade path exists if wanted), but hand-rolled
 * counters are honest about what they are: enough to demonstrate the
 * observability instinct without over-building infrastructure nobody asked
 * for at this scope.
 *
 * Thread-safety: AtomicLong is safe under concurrent increment from
 * multiple request threads — this matters here specifically because the
 * concurrency test fires 100 threads at the booking endpoint simultaneously,
 * and these counters need to come out right under that exact load.
 */
@Service
public class MetricsService {

    private final AtomicLong bookingAttempts = new AtomicLong();
    private final AtomicLong bookingSuccesses = new AtomicLong();
    private final AtomicLong bookingSoldOutRejections = new AtomicLong();

    private final AtomicLong paymentIntentsCreated = new AtomicLong();
    private final AtomicLong paymentsSucceeded = new AtomicLong();
    private final AtomicLong paymentsFailed = new AtomicLong();
    private final AtomicLong refunds = new AtomicLong();

    private final AtomicLong reservationsExpired = new AtomicLong();

    public void recordBookingAttempt() {
        bookingAttempts.incrementAndGet();
    }

    public void recordBookingSuccess() {
        bookingSuccesses.incrementAndGet();
    }

    public void recordBookingSoldOutRejection() {
        bookingSoldOutRejections.incrementAndGet();
    }

    public void recordPaymentIntentCreated() {
        paymentIntentsCreated.incrementAndGet();
    }

    public void recordPaymentSucceeded() {
        paymentsSucceeded.incrementAndGet();
    }

    public void recordPaymentFailed() {
        paymentsFailed.incrementAndGet();
    }

    public void recordRefund() {
        refunds.incrementAndGet();
    }

    public void recordReservationExpired() {
        reservationsExpired.incrementAndGet();
    }

    public Snapshot snapshot() {
        long attempts = bookingAttempts.get();
        long successes = bookingSuccesses.get();
        long payments = paymentsSucceeded.get() + paymentsFailed.get();

        double bookingSuccessRate = attempts == 0 ? 0.0 : (double) successes / attempts;
        double paymentFailureRate = payments == 0 ? 0.0 : (double) paymentsFailed.get() / payments;

        return new Snapshot(
            attempts,
            successes,
            bookingSoldOutRejections.get(),
            bookingSuccessRate,
            paymentIntentsCreated.get(),
            paymentsSucceeded.get(),
            paymentsFailed.get(),
            paymentFailureRate,
            refunds.get(),
            reservationsExpired.get()
        );
    }

    /** Immutable point-in-time read of all counters plus the two derived rates the brief asks for. */
    public record Snapshot(
        long bookingAttempts,
        long bookingSuccesses,
        long bookingSoldOutRejections,
        double bookingSuccessRate,
        long paymentIntentsCreated,
        long paymentsSucceeded,
        long paymentsFailed,
        double paymentFailureRate,
        long refunds,
        long reservationsExpired
    ) {
    }
}
