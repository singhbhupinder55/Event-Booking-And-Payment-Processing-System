package com.portfolio.eventbooking.exception;

/**
 * Thrown when a booking request cannot be satisfied because there aren't
 * enough available seats remaining. Maps to HTTP 409 Conflict — the request
 * was valid, but conflicts with the current state of the resource (capacity).
 */
public class EventSoldOutException extends RuntimeException {

    private final Long eventId;
    private final int requested;
    private final int available;

    public EventSoldOutException(Long eventId, int requested, int available) {
        super("Event %d sold out: requested %d seats, only %d available".formatted(eventId, requested, available));
        this.eventId = eventId;
        this.requested = requested;
        this.available = available;
    }

    public Long getEventId() {
        return eventId;
    }

    public int getRequested() {
        return requested;
    }

    public int getAvailable() {
        return available;
    }
}
