package com.portfolio.eventbooking.dto;

import com.portfolio.eventbooking.domain.Event;

import java.time.Instant;

public record EventResponse(
    Long id,
    String name,
    String description,
    int totalCapacity,
    int availableSeats,
    long priceCents,
    String currency,
    Instant startsAt
) {
    public static EventResponse from(Event event) {
        return new EventResponse(
            event.getId(),
            event.getName(),
            event.getDescription(),
            event.getTotalCapacity(),
            event.getAvailableSeats(),
            event.getPriceCents(),
            event.getCurrency(),
            event.getStartsAt()
        );
    }
}
