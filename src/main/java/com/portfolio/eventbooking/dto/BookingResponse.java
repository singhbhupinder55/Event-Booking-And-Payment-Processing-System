package com.portfolio.eventbooking.dto;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;

import java.time.Instant;

public record BookingResponse(
    Long id,
    Long eventId,
    String userReference,
    BookingStatus status,
    int seatsRequested,
    long amountCents,
    String currency,
    String stripePaymentIntentId,
    Instant reservationExpiresAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getEventId(),
            booking.getUserReference(),
            booking.getStatus(),
            booking.getSeatsRequested(),
            booking.getAmountCents(),
            booking.getCurrency(),
            booking.getStripePaymentIntentId(),
            booking.getReservationExpiresAt(),
            booking.getCreatedAt(),
            booking.getUpdatedAt()
        );
    }
}
