package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.domain.TriggerType;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import com.portfolio.eventbooking.repository.ProcessedStripeEventRepository;
import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.domain.ProcessedStripeEvent;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Charge;
import com.stripe.model.StripeObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Processes Stripe webhook events. Two distinct correctness concerns live
 * here, per the brief:
 *
 * 1. IDEMPOTENCY: Stripe explicitly documents that the same event may be
 *    delivered more than once (retries on timeout, at-least-once delivery).
 *    We dedupe on Stripe's own event ID (evt_...) via the
 *    processed_stripe_events table — NOT the same mechanism as the
 *    client-facing Idempotency-Key table, which solves a different problem
 *    (our API clients retrying, not Stripe retrying webhook delivery).
 *
 * 2. SOURCE OF TRUTH: we never trust a client's claim that payment
 *    succeeded. Only this handler, reacting to a verified Stripe webhook,
 *    is allowed to move a booking from PENDING to CONFIRMED.
 *
 * Dedup mechanism: same pattern as IdempotencyService — attempt to insert a
 * ProcessedStripeEvent row keyed on the unique stripe_event_id; if that
 * insert collides, we've already processed (or are concurrently
 * processing) this exact event, so we skip business logic entirely. This
 * is simpler than the client-facing flow because we don't need to replay a
 * stored HTTP response — Stripe only cares about a 2xx response, not what
 * body we return.
 */
@Service
public class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final ProcessedStripeEventRepository processedStripeEventRepository;
    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final AuditLogService auditLogService;
    private final MetricsService metricsService;

    public StripeWebhookService(ProcessedStripeEventRepository processedStripeEventRepository,
                                 BookingRepository bookingRepository,
                                 EventRepository eventRepository,
                                 AuditLogService auditLogService,
                                 MetricsService metricsService) {
        this.processedStripeEventRepository = processedStripeEventRepository;
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.auditLogService = auditLogService;
        this.metricsService = metricsService;
    }

    /**
     * Entry point called by the webhook controller after signature
     * verification. Returns true if this event was newly processed, false
     * if it was a duplicate delivery we already handled (both are "success"
     * from the webhook's perspective — the controller returns 200 either way).
     */
    @Transactional
    public boolean processEvent(String stripeEventId, String eventType, StripeObject eventDataObject) {
        if (!claimEvent(stripeEventId, eventType)) {
            log.info("Duplicate Stripe webhook delivery ignored: eventId={} type={}", stripeEventId, eventType);
            return false;
        }

        switch (eventType) {
            case "payment_intent.succeeded" -> handlePaymentSucceeded((PaymentIntent) eventDataObject, stripeEventId);
            case "payment_intent.payment_failed" -> handlePaymentFailed((PaymentIntent) eventDataObject, stripeEventId);
            case "charge.refunded" -> handleChargeRefunded((Charge) eventDataObject, stripeEventId);
            default -> log.info("Received Stripe event type with no handler, ignoring: {}", eventType);
        }

        return true;
    }

    /**
     * Attempts to atomically claim this Stripe event ID. Returns true if
     * this is the first time we've seen it (caller should proceed), false
     * if it's a duplicate (caller should skip). Relies on the unique index
     * on stripe_event_id (see V5 migration) the same way IdempotencyService
     * relies on the (key, path) unique index.
     */
    private boolean claimEvent(String stripeEventId, String eventType) {
        if (processedStripeEventRepository.existsByStripeEventId(stripeEventId)) {
            return false;
        }
        try {
            processedStripeEventRepository.save(new ProcessedStripeEvent(stripeEventId, eventType, null));
            return true;
        } catch (DataIntegrityViolationException raceLost) {
            // Another concurrent delivery of the same event claimed it
            // between our check and our insert. Treat as a duplicate.
            return false;
        }
    }

    private void handlePaymentSucceeded(PaymentIntent intent, String stripeEventId) {
        Long bookingId = extractBookingId(intent.getMetadata());
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("payment_intent.succeeded for unknown booking: bookingId={} paymentIntentId={}",
                bookingId, intent.getId());
            return;
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            // Already confirmed (e.g. from an earlier, non-duplicate event we
            // already handled) or in a terminal state. Nothing to do —
            // this guards against any edge case where claimEvent's dedup
            // didn't catch a logical duplicate (e.g. Stripe sending both
            // payment_intent.succeeded for the same intent twice under
            // different event IDs, which it documents as possible).
            log.info("payment_intent.succeeded ignored, booking {} already in status {}",
                bookingId, booking.getStatus());
            return;
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.transitionTo(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        auditLogService.record(
            booking.getId(), booking.getEventId(), previousStatus, BookingStatus.CONFIRMED,
            TriggerType.STRIPE_WEBHOOK, stripeEventId,
            "{\"paymentIntentId\":\"%s\"}".formatted(intent.getId()));

        metricsService.recordPaymentSucceeded();

        log.info("Booking confirmed via webhook: bookingId={} paymentIntentId={}", bookingId, intent.getId());
    }

    private void handlePaymentFailed(PaymentIntent intent, String stripeEventId) {
        Long bookingId = extractBookingId(intent.getMetadata());
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("payment_intent.payment_failed for unknown booking: bookingId={}", bookingId);
            return;
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("payment_intent.payment_failed ignored, booking {} already in status {}",
                bookingId, booking.getStatus());
            return;
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.transitionTo(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Release the seat back to the pool — the same lock-acquiring path
        // BookingService uses, since this is also a write to availableSeats
        // that must be serialized against concurrent booking attempts.
        Event event = eventRepository.findByIdForUpdate(booking.getEventId()).orElseThrow();
        event.releaseSeats(booking.getSeatsRequested());
        eventRepository.save(event);

        auditLogService.record(
            booking.getId(), booking.getEventId(), previousStatus, BookingStatus.CANCELLED,
            TriggerType.STRIPE_WEBHOOK, stripeEventId,
            "{\"paymentIntentId\":\"%s\",\"reason\":\"payment_failed\"}".formatted(intent.getId()));

        metricsService.recordPaymentFailed();

        log.info("Booking cancelled (payment failed) via webhook: bookingId={} paymentIntentId={}",
            bookingId, intent.getId());
    }

    private void handleChargeRefunded(Charge charge, String stripeEventId) {
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            log.warn("charge.refunded event with no associated PaymentIntent, chargeId={}", charge.getId());
            return;
        }

        Optional<Booking> bookingOpt = bookingRepository.findByStripePaymentIntentId(paymentIntentId);
        if (bookingOpt.isEmpty()) {
            log.warn("charge.refunded for unknown PaymentIntent: {}", paymentIntentId);
            return;
        }
        Booking booking = bookingOpt.get();

        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            log.info("charge.refunded ignored, booking {} not in CONFIRMED status (status={})",
                booking.getId(), booking.getStatus());
            return;
        }

        BookingStatus previousStatus = booking.getStatus();
        booking.transitionTo(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Event event = eventRepository.findByIdForUpdate(booking.getEventId()).orElseThrow();
        event.releaseSeats(booking.getSeatsRequested());
        eventRepository.save(event);

        auditLogService.record(
            booking.getId(), booking.getEventId(), previousStatus, BookingStatus.CANCELLED,
            TriggerType.STRIPE_WEBHOOK, stripeEventId,
            "{\"paymentIntentId\":\"%s\",\"reason\":\"refunded\"}".formatted(paymentIntentId));

        metricsService.recordRefund();

        log.info("Booking cancelled (refunded) via webhook: bookingId={} paymentIntentId={}",
            booking.getId(), paymentIntentId);
    }

    private Long extractBookingId(java.util.Map<String, String> metadata) {
        String raw = metadata.get("bookingId");
        if (raw == null) {
            throw new IllegalStateException("PaymentIntent metadata missing bookingId");
        }
        return Long.parseLong(raw);
    }
}
