package com.portfolio.eventbooking.integration;

import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.exception.EventSoldOutException;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import com.portfolio.eventbooking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * THE concurrency-correctness test for the whole project.
 *
 * Scenario: an event has exactly 10 seats available. 100 threads attempt to
 * book 1 seat each, all at roughly the same instant. We assert:
 *   - exactly 10 bookings succeed
 *   - exactly 90 are cleanly rejected with EventSoldOutException (mapped to
 *     409 at the HTTP layer — see GlobalExceptionHandler)
 *   - the event's available_seats column ends at exactly 0, never negative
 *   - no thread deadlocks or times out
 *
 * This runs against real MySQL (via AbstractIntegrationTest's Testcontainers
 * setup), so it's actually exercising InnoDB's row-lock queueing behavior
 * under SELECT ... FOR UPDATE — not a mock, not H2's approximation of it.
 */
class BookingConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private static final int AVAILABLE_SEATS = 10;
    private static final int CONCURRENT_REQUESTS = 100;

    @Test
    void exactlyTenOfOneHundredConcurrentBookingsSucceedForTenAvailableSeats() throws InterruptedException, ExecutionException, TimeoutException {
        Event event = new Event(
            "Concurrency Test Concert", "10 seats, 100 bidders", AVAILABLE_SEATS,
            5000L, "USD", Instant.now().plusSeconds(3600));
        event = eventRepository.save(event);
        Long eventId = event.getId();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger soldOutCount = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

        List<Future<?>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            final String userRef = "user-" + i;
            futures.add(executor.submit(() -> {
                try {
                    // All threads wait here so they hit the booking call as
                    // close to simultaneously as the JVM scheduler allows —
                    // this maximizes actual lock contention rather than
                    // threads trickling in one at a time.
                    readyLatch.countDown();
                    startLatch.await();

                    bookingService.createBooking(eventId, userRef, 1);
                    successCount.incrementAndGet();
                } catch (EventSoldOutException e) {
                    soldOutCount.incrementAndGet();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                }
            }));
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown(); // release all 100 threads at once

        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS); // would throw TimeoutException on a real deadlock
        }
        executor.shutdown();

        assertThat(unexpectedErrorCount.get())
            .as("no thread should hit an unexpected exception (deadlock, etc.)")
            .isZero();
        assertThat(successCount.get())
            .as("exactly the available seat count should succeed")
            .isEqualTo(AVAILABLE_SEATS);
        assertThat(soldOutCount.get())
            .as("everyone else should be cleanly rejected as sold out")
            .isEqualTo(CONCURRENT_REQUESTS - AVAILABLE_SEATS);

        Event finalEvent = eventRepository.findById(eventId).orElseThrow();
        assertThat(finalEvent.getAvailableSeats())
            .as("available seats must land at exactly 0 — never negative (oversold) or positive (lost bookings)")
            .isZero();

        long confirmedPendingBookings = bookingRepository.findAll().stream()
            .filter(b -> b.getEventId().equals(eventId))
            .count();
        assertThat(confirmedPendingBookings)
            .as("exactly 10 booking rows should exist for this event — no phantom extra rows from a lost update")
            .isEqualTo(AVAILABLE_SEATS);
    }
}
