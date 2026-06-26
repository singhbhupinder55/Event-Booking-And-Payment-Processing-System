package com.portfolio.eventbooking.exception;

/** Generic not-found exception. Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException forEvent(Long eventId) {
        return new ResourceNotFoundException("Event not found: " + eventId);
    }

    public static ResourceNotFoundException forBooking(Long bookingId) {
        return new ResourceNotFoundException("Booking not found: " + bookingId);
    }
}
