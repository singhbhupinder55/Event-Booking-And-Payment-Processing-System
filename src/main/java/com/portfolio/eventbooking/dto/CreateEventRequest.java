package com.portfolio.eventbooking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateEventRequest(
    @NotBlank(message = "name is required")
    String name,

    String description,

    @NotNull(message = "totalCapacity is required")
    @Min(value = 0, message = "totalCapacity cannot be negative")
    Integer totalCapacity,

    @NotNull(message = "priceCents is required")
    @Min(value = 0, message = "priceCents cannot be negative")
    Long priceCents,

    @NotBlank(message = "currency is required")
    String currency,

    @NotNull(message = "startsAt is required")
    Instant startsAt
) {
}
