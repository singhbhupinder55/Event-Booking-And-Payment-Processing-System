package com.portfolio.eventbooking.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * The booking lifecycle states and the valid transitions between them.
 *
 * State diagram:
 *
 *   PENDING ──(payment succeeds)──▶ CONFIRMED ──(cancellation + refund)──▶ CANCELLED
 *      │
 *      ├──(payment fails)──▶ CANCELLED
 *      │
 *      └──(reservation window elapses unpaid)──▶ EXPIRED
 *
 * Notes:
 * - CONFIRMED and CANCELLED and EXPIRED are all terminal-ish, but CONFIRMED
 *   can still move to CANCELLED via a refund. EXPIRED and the failed-payment
 *   CANCELLED are true terminal states with no way out.
 * - There is intentionally no PENDING -> EXPIRED -> CONFIRMED path: once a
 *   reservation has expired and the seat has been released back to the pool,
 *   that booking is dead. A new booking must be created if the user retries.
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    private static final Set<BookingStatus> NO_TRANSITIONS = EnumSet.noneOf(BookingStatus.class);

    /**
     * Returns the set of statuses this status is allowed to transition into.
     * Enforced centrally here so the rule lives in exactly one place instead
     * of being scattered (and potentially duplicated/contradicted) across
     * service methods.
     */
    public Set<BookingStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED, EXPIRED);
            case CONFIRMED -> EnumSet.of(CANCELLED); // refund path
            case CANCELLED -> NO_TRANSITIONS;
            case EXPIRED -> NO_TRANSITIONS;
        };
    }

    public boolean canTransitionTo(BookingStatus target) {
        return allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
