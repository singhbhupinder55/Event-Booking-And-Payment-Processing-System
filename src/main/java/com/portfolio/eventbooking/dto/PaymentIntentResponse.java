package com.portfolio.eventbooking.dto;

import com.stripe.model.PaymentIntent;

/**
 * What we expose to the client after creating a PaymentIntent. We
 * deliberately only return the clientSecret (which the client needs to
 * confirm payment via Stripe.js/Elements) and a few non-sensitive fields —
 * never the full Stripe PaymentIntent object, which can carry more detail
 * than a client needs.
 */
public record PaymentIntentResponse(
    String paymentIntentId,
    String clientSecret,
    long amountCents,
    String currency,
    String status
) {
    public static PaymentIntentResponse from(PaymentIntent intent) {
        return new PaymentIntentResponse(
            intent.getId(),
            intent.getClientSecret(),
            intent.getAmount(),
            intent.getCurrency(),
            intent.getStatus()
        );
    }
}
