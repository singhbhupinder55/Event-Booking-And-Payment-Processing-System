package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.domain.TriggerType;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds the transactional, per-booking expiry logic. Deliberately
 * separated from ReservationExpiryScheduler (which holds the @Scheduled
 * sweep loop) rather than having one class do both.
 *
 * WHY THIS SPLIT EXISTS — Spring AOP self-invocation pitfall: @Transactional
 * is implemented via a dynamic proxy that wraps the bean. That proxy only
 * intercepts calls that arrive from OUTSIDE the bean (i.e. through another
 * Spring-managed bean calling in). A method calling another method on
 * `this` within the SAME class bypasses the proxy entirely, silently
 * ignoring @Transactional on the inner call. This is exactly the bug we
 * hit during development: expireOneBooking's @Transactional was being
 * silently ignored when called from sweepExpiredReservations in the same
 * class, causing "no transaction in progress" failures on
 * findByIdForUpdate. Splitting into two beans means the scheduler's call
 * into this service crosses a real proxy boundary, so @Transactional
 * actually applies.
 */
@Service
public class ReservationExpiryService {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryService.class);

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;

    public ReservationExpiryService(BookingRepository bookingRepository,
                                     EventRepository eventRepository,
                                     AuditLogService auditLogService,
                                     MetricsService metricsService) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
    }

    /**
     * @return true if this booking was actually expired, false if it was
     * skipped because it had already moved out of PENDING by the time we
     * got to processing it (e.g. a payment webhook landed in the gap
     * between the sweep's query and this method running).
     */
    @Transactional
    public boolean expireOneBooking(Long bookingId, String correlationId) {
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return false;
        }

        // Re-check status here rather than trusting the sweep query alone —
        // time has passed since that query ran, and a webhook could have
        // confirmed or cancelled this booking in the interim.
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("Skipping booking {} in expiry sweep — no longer PENDING (now {})",
                bookingId, booking.getStatus());
            return false;
        }

        booking.transitionTo(BookingStatus.EXPIRED);
        bookingRepository.save(booking);

        Event event = eventRepository.findByIdForUpdate(booking.getEventId())
            .orElseThrow(() -> new IllegalStateException(
                "Event %d for booking %d vanished during expiry sweep".formatted(booking.getEventId(), bookingId)));
        event.releaseSeats(booking.getSeatsRequested());
        eventRepository.save(event);

        auditLogService.record(
            booking.getId(), booking.getEventId(), BookingStatus.PENDING, BookingStatus.EXPIRED,
            TriggerType.SYSTEM_EXPIRY, "scheduler",
            "{\"reservationExpiresAt\":\"%s\"}".formatted(booking.getReservationExpiresAt()));

        metricsService.recordReservationExpired();

        log.info("Booking expired and seats released: bookingId={} eventId={} seats={}",
            bookingId, booking.getEventId(), booking.getSeatsRequested());

        return true;
    }
}
