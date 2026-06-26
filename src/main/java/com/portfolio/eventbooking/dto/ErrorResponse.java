package com.portfolio.eventbooking.dto;

import java.time.Instant;
import java.util.List;

/**
 * Consistent error response shape across the whole API. The brief calls
 * for "structured error responses (consistent error shape, not raw stack
 * traces)" — this is that shape. Every error the client sees, regardless
 * of cause, comes back looking like this.
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<String> details
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponse withDetails(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponse(Instant.now(), status, error, message, path, details);
    }
}
