package com.portfolio.eventbooking.repository;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByStripePaymentIntentId(String stripePaymentIntentId);

    /**
     * Used by the expiry sweep job: find PENDING bookings whose reservation
     * window has elapsed so their seats can be released back to the pool.
     */
    List<Booking> findByStatusAndReservationExpiresAtBefore(BookingStatus status, Instant cutoff);
}
