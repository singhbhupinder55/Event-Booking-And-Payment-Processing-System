package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Per the brief: "Reservations that aren't paid within a time window
 * (e.g. 10 minutes) auto-expire and release the seat."
 *
 * Holds the @Scheduled sweep loop only. The actual transactional,
 * per-booking work lives in ReservationExpiryService — see that class's
 * Javadoc for why this is split into two beans rather than one (a Spring
 * AOP self-invocation pitfall we hit during development: @Transactional
 * on a method is silently ignored when called from another method in the
 * SAME class, because Spring's transactional proxy only intercepts calls
 * arriving from outside the bean).
 *
 * Each booking is expired in its OWN transaction (one call per booking to
 * the other bean), not one transaction for the whole sweep. This is
 * deliberate: if 50 bookings are expired in one sweep run and booking #37
 * hits an unexpected error, we don't want that to roll back the 36 we
 * already correctly expired. One slow/bad row should not block the rest
 * of the batch.
 */
@Component
public class ReservationExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryScheduler.class);

    private final BookingRepository bookingRepository;
    private final ReservationExpiryService reservationExpiryService;

    public ReservationExpiryScheduler(BookingRepository bookingRepository,
                                       ReservationExpiryService reservationExpiryService) {
        this.bookingRepository = bookingRepository;
        this.reservationExpiryService = reservationExpiryService;
    }

    /**
     * Runs every 60 seconds. A 10-minute reservation window doesn't need
     * sub-minute sweep precision — a seat sitting "reserved" for up to ~60
     * extra seconds past its technical expiry is an acceptable tradeoff
     * against running a DB scan every few seconds for no real benefit.
     */
    @Scheduled(fixedRate = 60_000)
    public void sweepExpiredReservations() {
        List<Booking> candidates =
            bookingRepository.findByStatusAndReservationExpiresAtBefore(BookingStatus.PENDING, Instant.now());

        if (candidates.isEmpty()) {
            return;
        }

        // One correlation ID for the whole sweep run, so every audit row
        // this run produces can be grepped together as "this batch."
        String sweepCorrelationId = "expiry-sweep-" + UUID.randomUUID();

        int expiredCount = 0;
        for (Booking candidate : candidates) {
            try {
                // This call crosses a real Spring bean boundary (a
                // different bean than `this`), so reservationExpiryService's
                // @Transactional annotation on expireOneBooking is honored
                // correctly — unlike the same-class self-invocation this
                // code used to have.
                if (reservationExpiryService.expireOneBooking(candidate.getId(), sweepCorrelationId)) {
                    expiredCount++;
                }
            } catch (Exception e) {
                // Log and move on to the next booking — one failure must not
                // abort the rest of the sweep (see class-level note).
                log.error("Failed to expire booking {} during reservation sweep: {}",
                    candidate.getId(), e.getMessage(), e);
            }
        }

        log.info("Reservation expiry sweep complete: {} of {} candidate bookings expired",
            expiredCount, candidates.size());
    }
}
