package com.portfolio.eventbooking.domain;

import java.util.Objects;

/**
 * Small value object pairing an integer-cents amount with its currency.
 * We never use float/double for money (see README "Design Decisions") —
 * this wraps the long so call sites read as "120000 cents, USD" instead of
 * a bare number that's easy to mix up with a different currency or unit.
 *
 * Deliberately minimal: no arithmetic operators beyond what the booking flow
 * actually needs. This is not a general-purpose currency math library.
 */
public final class Money {

    private final long amountCents;
    private final String currency;

    private Money(long amountCents, String currency) {
        if (amountCents < 0) {
            throw new IllegalArgumentException("amountCents cannot be negative: " + amountCents);
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be a 3-letter ISO code, got: " + currency);
        }
        this.amountCents = amountCents;
        this.currency = currency.toUpperCase();
    }

    public static Money of(long amountCents, String currency) {
        return new Money(amountCents, currency);
    }

    public static Money usd(long amountCents) {
        return new Money(amountCents, "USD");
    }

    /** Multiplies by a positive integer quantity (e.g. price-per-seat * seats). */
    public Money multiply(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative: " + quantity);
        }
        return new Money(Math.multiplyExact(this.amountCents, (long) quantity), this.currency);
    }

    public long amountCents() {
        return amountCents;
    }

    public String currency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return amountCents == other.amountCents && currency.equals(other.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amountCents, currency);
    }

    @Override
    public String toString() {
        return amountCents + " " + currency;
    }
}
