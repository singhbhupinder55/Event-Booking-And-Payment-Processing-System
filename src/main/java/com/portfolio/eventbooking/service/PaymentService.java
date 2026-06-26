package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.exception.InvalidBookingStateTransitionException;
import com.portfolio.eventbooking.exception.ResourceNotFoundException;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Creates and attaches Stripe PaymentIntents to bookings.
 *
 * Uses the PaymentIntents authorize-then-capture flow (manual capture is
 * NOT used here — we use automatic capture, meaning Stripe captures funds
 * as soon as the PaymentIntent is confirmed by the client, which is the
 * standard flow for "pay now" bookings like this one). The brief calls out
 * PaymentIntents over simple Charges specifically because PaymentIntents
 * model the full lifecycle (requires_payment_method -> requires_
 * confirmation -> processing -> succeeded/failed) and are SCA/3D-Secure
 * ready, which Charges are not — that's the realistic, production pattern
 * a real fintech/payments team would actually use, even for a flow this
 * simple. See README "Design Decisions" for the full comparison.
 *
 * We do NOT trust this method's return value as proof of payment. Creating
 * a PaymentIntent only means the client now CAN attempt to pay — the
 * booking stays PENDING. The booking only moves to CONFIRMED when Stripe's
 * webhook tells us payment_intent.succeeded actually happened (see
 * StripeWebhookService). This is the brief's core requirement: "don't just
 * trust the client's 'payment succeeded' response; the webhook is the
 * source of truth."
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final MetricsService metricsService;

    public PaymentService(BookingRepository bookingRepository, MetricsService metricsService) {
        this.bookingRepository = bookingRepository;
        this.metricsService = metricsService;
    }

    @Transactional
    public PaymentIntent createPaymentIntentForBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> ResourceNotFoundException.forBooking(bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateTransitionException(bookingId, booking.getStatus(), BookingStatus.PENDING);
        }

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(booking.getAmountCents())
                .setCurrency(booking.getCurrency().toLowerCase())
                // metadata.bookingId is how the webhook handler maps a Stripe
                // event back to OUR booking — Stripe events don't carry our
                // primary keys natively, so we stamp it ourselves here.
                .putAllMetadata(Map.of("bookingId", bookingId.toString()))
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build())
                .build();

            PaymentIntent intent = PaymentIntent.create(params);

            booking.attachPaymentIntent(intent.getId());
            bookingRepository.save(booking);

            metricsService.recordPaymentIntentCreated();

            log.info("PaymentIntent created: bookingId={} paymentIntentId={} amountCents={}",
                bookingId, intent.getId(), booking.getAmountCents());

            return intent;
        } catch (StripeException e) {
            // A Stripe API-level failure here (network, invalid request, etc.)
            // is NOT the same as a declined card — it means we couldn't even
            // create the PaymentIntent. The booking stays PENDING and will
            // simply expire via the normal reservation-expiry sweep if the
            // client never successfully retries.
            log.error("Failed to create PaymentIntent for booking {}: {}", bookingId, e.getMessage());
            throw new PaymentIntentCreationException(bookingId, e);
        }
    }

    public static class PaymentIntentCreationException extends RuntimeException {
        public PaymentIntentCreationException(Long bookingId, StripeException cause) {
            super("Failed to create PaymentIntent for booking " + bookingId + ": " + cause.getMessage(), cause);
        }
    }
}
