package com.portfolio.eventbooking.exception;

/**
 * Thrown when a request reuses an Idempotency-Key that matches a request
 * currently being processed by another in-flight request (same key, same
 * body, but the original hasn't finished yet). We deliberately do not block
 * and wait for the original to finish — that would tie up a thread and
 * risk a long hang if the original request is itself stuck. Instead we
 * reject immediately with 409 and let the client's normal retry behavior
 * (with backoff) resolve it once the original completes.
 */
public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException(String idempotencyKey) {
        super("A request with Idempotency-Key '%s' is already being processed".formatted(idempotencyKey));
    }
}
