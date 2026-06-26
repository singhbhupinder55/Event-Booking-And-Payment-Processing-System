package com.portfolio.eventbooking.exception;

import com.portfolio.eventbooking.domain.BookingStatus;

/**
 * Thrown when code attempts an illegal booking state transition
 * (e.g. CONFIRMED -> PENDING, or any transition out of a terminal state).
 * Maps to HTTP 422 Unprocessable Entity — the request was well-formed,
 * but the operation is not valid given the current state.
 */
public class InvalidBookingStateTransitionException extends RuntimeException {

    private final Long bookingId;
    private final BookingStatus from;
    private final BookingStatus to;

    public InvalidBookingStateTransitionException(Long bookingId, BookingStatus from, BookingStatus to) {
        super("Booking %d cannot transition from %s to %s".formatted(bookingId, from, to));
        this.bookingId = bookingId;
        this.from = from;
        this.to = to;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public BookingStatus getFrom() {
        return from;
    }

    public BookingStatus getTo() {
        return to;
    }
}
