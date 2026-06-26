package com.portfolio.eventbooking.domain;

/**
 * What category of actor caused a booking state transition.
 * Recorded on every audit_log row so "what happened to booking #4471"
 * can distinguish "the user cancelled it" from "Stripe told us payment failed"
 * from "the scheduler expired it."
 */
public enum TriggerType {
    USER_ACTION,
    STRIPE_WEBHOOK,
    SYSTEM_EXPIRY
}
