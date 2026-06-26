package com.portfolio.eventbooking.exception;

/**
 * Thrown when a client reuses an Idempotency-Key on the same endpoint but
 * with a different request body than the original call. This is a client
 * bug (idempotency keys must be paired 1:1 with a single logical request),
 * so it maps to 409 Conflict rather than silently returning the original
 * response or the new one.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency-Key '%s' was already used with a different request body".formatted(idempotencyKey));
    }
}
