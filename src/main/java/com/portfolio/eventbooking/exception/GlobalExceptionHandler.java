package com.portfolio.eventbooking.exception;

import com.portfolio.eventbooking.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Translates domain exceptions into the structured ErrorResponse shape with
 * the correct HTTP status per the brief:
 *   - 404 resource not found
 *   - 409 sold out (conflict with current resource state)
 *   - 422 invalid state transition (well-formed request, invalid given state)
 *   - 400 validation failure (malformed/missing request fields)
 *   - 500 fallback for anything unexpected — never leaks a raw stack trace
 *     to the client; the trace goes to the logs (with correlation ID) instead.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of(404, "NOT_FOUND", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(EventSoldOutException.class)
    public ResponseEntity<ErrorResponse> handleSoldOut(EventSoldOutException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "SOLD_OUT", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InvalidBookingStateTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidBookingStateTransitionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of(422, "INVALID_STATE_TRANSITION", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_BODY", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyInProgress(
            IdempotencyInProgressException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of(409, "IDEMPOTENCY_KEY_IN_PROGRESS", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(
            org.springframework.web.bind.MissingRequestHeaderException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of(400, "MISSING_REQUIRED_HEADER", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.withDetails(400, "VALIDATION_FAILED", "Request validation failed", request.getRequestURI(), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // Full exception + stack trace goes to the logs (correlation ID is in
        // MDC already via CorrelationIdFilter), but the client only ever sees
        // a generic message — never the raw exception/stack trace.
        log.error("Unhandled exception processing {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.of(500, "INTERNAL_ERROR", "An unexpected error occurred", request.getRequestURI()));
    }
}
