package com.portfolio.eventbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/events/{eventId}/bookings.
 * eventId comes from the path, not the body — kept out of this record.
 */
public record CreateBookingRequest(

    @NotBlank(message = "userReference is required")
    String userReference,

    @NotNull(message = "seats is required")
    @Min(value = 1, message = "seats must be at least 1")
    Integer seats
) {
}
