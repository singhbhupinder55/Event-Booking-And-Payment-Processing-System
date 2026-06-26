package com.portfolio.eventbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Dedup record for inbound Stripe webhook deliveries, keyed on Stripe's own
 * event id (evt_...). Stripe explicitly documents that webhooks may be
 * delivered more than once for the same event — this table is what makes
 * our webhook handler safe against that.
 */
@Entity
@Table(name = "processed_stripe_events")
public class ProcessedStripeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stripe_event_id", nullable = false)
    private String stripeEventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedStripeEvent() {
        // JPA
    }

    public ProcessedStripeEvent(String stripeEventId, String eventType, Long bookingId) {
        this.stripeEventId = stripeEventId;
        this.eventType = eventType;
        this.bookingId = bookingId;
        this.processedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getStripeEventId() {
        return stripeEventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
