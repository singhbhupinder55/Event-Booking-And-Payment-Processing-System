package com.portfolio.eventbooking.domain;

import com.portfolio.eventbooking.exception.InvalidBookingStateTransitionException;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_reference", nullable = false)
    private String userReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "seats_requested", nullable = false)
    private int seatsRequested;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "stripe_payment_intent_id")
    private String stripePaymentIntentId;

    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Booking() {
        // JPA
    }

    public Booking(Long eventId, String userReference, int seatsRequested, long amountCents,
                    String currency, Instant reservationExpiresAt) {
        this.eventId = eventId;
        this.userReference = userReference;
        this.status = BookingStatus.PENDING;
        this.seatsRequested = seatsRequested;
        this.amountCents = amountCents;
        this.currency = currency;
        this.reservationExpiresAt = reservationExpiresAt;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Centralized transition method — every status change in the system goes
     * through here, so the BookingStatus.canTransitionTo() rule is actually
     * enforced rather than just documented. Throws if the transition is illegal.
     */
    public void transitionTo(BookingStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidBookingStateTransitionException(this.id, this.status, target);
        }
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public void attachPaymentIntent(String paymentIntentId) {
        this.stripePaymentIntentId = paymentIntentId;
        this.updatedAt = Instant.now();
    }

    public boolean isExpired(Instant now) {
        return this.status == BookingStatus.PENDING && now.isAfter(this.reservationExpiresAt);
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public String getUserReference() {
        return userReference;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public int getSeatsRequested() {
        return seatsRequested;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public Instant getReservationExpiresAt() {
        return reservationExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
