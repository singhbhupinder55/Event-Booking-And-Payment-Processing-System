package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.*;
import com.portfolio.eventbooking.exception.EventSoldOutException;
import com.portfolio.eventbooking.exception.ResourceNotFoundException;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Owns the booking creation flow. This is the concurrency-critical path:
 * see {@link EventRepository#findByIdForUpdate} for why we take a
 * pessimistic row lock here rather than relying on optimistic locking or
 * application-level synchronization.
 *
 * Lock ordering note: this method only ever locks a single Event row per
 * call, and never locks more than one Event in the same transaction, so
 * there is no deadlock risk from inconsistent lock-acquisition order across
 * concurrent requests for *different* events. Concurrent requests for the
 * *same* event simply serialize on that one row's lock.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;
    private final long reservationWindowMinutes;

    public BookingService(EventRepository eventRepository,
                           BookingRepository bookingRepository,
                           AuditLogService auditLogService,
                           MetricsService metricsService,
                           @Value("${booking.reservation-window-minutes}") long reservationWindowMinutes) {
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
        this.reservationWindowMinutes = reservationWindowMinutes;
    }

    /**
     * Creates a PENDING booking for the given event, decrementing available
     * seats atomically with respect to other concurrent booking attempts on
     * the same event.
     *
     * Transaction boundary: the SELECT ... FOR UPDATE, the seat decrement,
     * the booking insert, and the audit log insert all happen inside one
     * transaction. The row lock is held for the entire transaction and
     * released on commit/rollback — so the critical section is "as short as
     * possible while still being correct," not "as short as physically
     * achievable." We don't call out to Stripe inside this transaction
     * (that happens in a follow-up step against the now-committed PENDING
     * booking) specifically so the lock isn't held across a network call to
     * a third party — that would turn a fast row lock into a slow one held
     * hostage by Stripe's latency.
     */
    @Transactional
    public Booking createBooking(Long eventId, String userReference, int seatsRequested) {
        metricsService.recordBookingAttempt();

        Event event = eventRepository.findByIdForUpdate(eventId)
            .orElseThrow(() -> ResourceNotFoundException.forEvent(eventId));

        if (seatsRequested > event.getAvailableSeats()) {
            log.info("Booking rejected: event={} requested={} available={}",
                eventId, seatsRequested, event.getAvailableSeats());
            metricsService.recordBookingSoldOutRejection();
            throw new EventSoldOutException(eventId, seatsRequested, event.getAvailableSeats());
        }

        event.reserveSeats(seatsRequested);
        eventRepository.save(event);

        Money amount = Money.of(event.getPriceCents(), event.getCurrency()).multiply(seatsRequested);
        Instant expiresAt = Instant.now().plus(reservationWindowMinutes, ChronoUnit.MINUTES);

        Booking booking = new Booking(
            eventId, userReference, seatsRequested, amount.amountCents(), amount.currency(), expiresAt);
        booking = bookingRepository.save(booking);

        auditLogService.record(
            booking.getId(), eventId, null, BookingStatus.PENDING,
            TriggerType.USER_ACTION, userReference,
            "{\"seatsRequested\":%d,\"amountCents\":%d}".formatted(seatsRequested, amount.amountCents())
        );

        metricsService.recordBookingSuccess();

        log.info("Booking created: bookingId={} eventId={} seats={} status=PENDING",
            booking.getId(), eventId, seatsRequested);

        return booking;
    }

    @Transactional(readOnly = true)
    public Booking getBooking(Long bookingId) {
        return bookingRepository.findById(bookingId)
            .orElseThrow(() -> ResourceNotFoundException.forBooking(bookingId));
    }
}
