package com.portfolio.eventbooking.controller;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.dto.BookingResponse;
import com.portfolio.eventbooking.dto.CreateBookingRequest;
import com.portfolio.eventbooking.dto.PaymentIntentResponse;
import com.portfolio.eventbooking.service.BookingService;
import com.portfolio.eventbooking.service.IdempotentRequestExecutor;
import com.portfolio.eventbooking.service.PaymentService;
import com.stripe.model.PaymentIntent;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Per the brief: "every payment-affecting endpoint accepts an
 * Idempotency-Key header." createBooking and createPaymentIntent are both
 * payment-affecting (one reserves a seat, the other creates a real Stripe
 * PaymentIntent against an amount) — getBooking is a plain read and
 * doesn't need one.
 *
 * The Idempotency-Key header is currently REQUIRED on both write endpoints.
 * This is a deliberate stance: an unprotected payment-affecting endpoint is
 * exactly the gap idempotency exists to close, so we don't allow callers to
 * opt out of it by omitting the header.
 */
@RestController
@RequestMapping("/api")
public class BookingController {

    private static final String BOOKING_CREATE_PATH = "/api/events/{eventId}/bookings";
    private static final String PAYMENT_INTENT_CREATE_PATH = "/api/bookings/{bookingId}/payment-intent";

    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    public BookingController(BookingService bookingService,
                              PaymentService paymentService,
                              IdempotentRequestExecutor idempotentRequestExecutor) {
        this.bookingService = bookingService;
        this.paymentService = paymentService;
        this.idempotentRequestExecutor = idempotentRequestExecutor;
    }

    @PostMapping("/events/{eventId}/bookings")
    public ResponseEntity<?> createBooking(
            @PathVariable Long eventId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {

        // Scope the key per-event-path, not just the template path, so a key
        // reused against a different eventId is correctly treated as a
        // different logical request rather than colliding in the table.
        String scopedPath = BOOKING_CREATE_PATH.replace("{eventId}", eventId.toString());

        return idempotentRequestExecutor.execute(
            idempotencyKey,
            scopedPath,
            request,
            HttpStatus.CREATED,
            () -> {
                Booking booking = bookingService.createBooking(eventId, request.userReference(), request.seats());
                return BookingResponse.from(booking);
            });
    }

    /**
     * Creates a Stripe PaymentIntent for an existing PENDING booking. The
     * client uses the returned clientSecret to confirm payment via
     * Stripe.js/Elements (or, for this portfolio project's Postman/curl
     * demo flow, a test-mode payment method can be confirmed directly
     * against the PaymentIntent via the Stripe API — see README).
     *
     * Idempotency-Key here protects against the client retrying this call
     * (e.g. on a flaky network) and ending up with two separate
     * PaymentIntents for the same booking, which would be confusing at best
     * and could mean the client ends up confirming the wrong one.
     */
    @PostMapping("/bookings/{bookingId}/payment-intent")
    public ResponseEntity<?> createPaymentIntent(
            @PathVariable Long bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String scopedPath = PAYMENT_INTENT_CREATE_PATH.replace("{bookingId}", bookingId.toString());

        return idempotentRequestExecutor.execute(
            idempotencyKey,
            scopedPath,
            java.util.Map.of("bookingId", bookingId), // no real request body for this endpoint; hash something deterministic and unique per booking
            HttpStatus.CREATED,
            () -> {
                PaymentIntent intent = paymentService.createPaymentIntentForBooking(bookingId);
                return PaymentIntentResponse.from(intent);
            });
    }

    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(@PathVariable Long bookingId) {
        Booking booking = bookingService.getBooking(bookingId);
        return ResponseEntity.ok(BookingResponse.from(booking));
    }
}
