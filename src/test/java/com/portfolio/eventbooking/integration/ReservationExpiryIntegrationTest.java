package com.portfolio.eventbooking.integration;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import com.portfolio.eventbooking.service.ReservationExpiryScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the reservation expiry sweep actually works against real MySQL:
 * a PENDING booking whose window has already elapsed gets expired and its
 * seats released, while a booking still within its window is left alone.
 */
class ReservationExpiryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void sweepExpiresOnlyBookingsPastTheirWindowAndLeavesOthersUntouched() {
        Event event = eventRepository.save(new Event(
            "Expiry Sweep Test Event", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600)));

        // Manually construct one booking already past its window...
        Booking expiredCandidate = new Booking(
            event.getId(), "user-expired", 3, 15000L, "USD", Instant.now().minusSeconds(60));
        expiredCandidate = bookingRepository.save(expiredCandidate);

        // ...and one still well within its window.
        Booking freshBooking = new Booking(
            event.getId(), "user-fresh", 2, 10000L, "USD", Instant.now().plusSeconds(600));
        freshBooking = bookingRepository.save(freshBooking);

        // Manually decrement seats to mirror what BookingService would have
        // done at creation time (we're bypassing that service here to set
        // up a specific, controlled scenario for the sweep).
        Event eventForSeatSetup = eventRepository.findById(event.getId()).orElseThrow();
        eventForSeatSetup.reserveSeats(5); // 3 + 2
        eventRepository.save(eventForSeatSetup);

        reservationExpiryScheduler.sweepExpiredReservations();

        Booking reloadedExpired = bookingRepository.findById(expiredCandidate.getId()).orElseThrow();
        Booking reloadedFresh = bookingRepository.findById(freshBooking.getId()).orElseThrow();
        Event reloadedEvent = eventRepository.findById(event.getId()).orElseThrow();

        assertThat(reloadedExpired.getStatus())
            .as("the past-window booking should be EXPIRED")
            .isEqualTo(BookingStatus.EXPIRED);
        assertThat(reloadedFresh.getStatus())
            .as("the still-valid booking should remain PENDING")
            .isEqualTo(BookingStatus.PENDING);
        assertThat(reloadedEvent.getAvailableSeats())
            .as("only the expired booking's 3 seats should be released (10 - 5 + 3 = 8)")
            .isEqualTo(8);
    }
}
