package com.portfolio.eventbooking.domain;

import com.portfolio.eventbooking.exception.InvalidBookingStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private Booking newPendingBooking() {
        return new Booking(1L, "user-1", 2, 10000L, "USD", Instant.now().plusSeconds(600));
    }

    @Test
    void newBookingStartsAsPending() {
        Booking booking = newPendingBooking();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void validTransitionSucceedsAndUpdatesStatus() {
        Booking booking = newPendingBooking();
        booking.transitionTo(BookingStatus.CONFIRMED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void invalidTransitionThrowsAndLeavesStatusUnchanged() {
        Booking booking = newPendingBooking();
        booking.transitionTo(BookingStatus.EXPIRED); // terminal

        assertThatThrownBy(() -> booking.transitionTo(BookingStatus.CONFIRMED))
            .isInstanceOf(InvalidBookingStateTransitionException.class);

        // The failed transition attempt must not have mutated state.
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
    }

    @Test
    void confirmedCanStillBeCancelledViaRefund() {
        Booking booking = newPendingBooking();
        booking.transitionTo(BookingStatus.CONFIRMED);
        booking.transitionTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void isExpiredReturnsTrueOnlyWhenPendingAndPastWindow() {
        Booking booking = new Booking(1L, "user-1", 1, 5000L, "USD", Instant.now().minusSeconds(1));
        assertThat(booking.isExpired(Instant.now())).isTrue();

        booking.transitionTo(BookingStatus.CONFIRMED);
        // Even though the window has elapsed, a CONFIRMED booking is not "expired" —
        // expiry only applies to unpaid PENDING reservations.
        assertThat(booking.isExpired(Instant.now())).isFalse();
    }
}
