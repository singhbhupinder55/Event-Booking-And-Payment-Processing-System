package com.portfolio.eventbooking.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Sets the Stripe SDK's global API key once at startup. The Stripe Java
 * SDK is designed around a single global Stripe.apiKey rather than a
 * per-call client instance (that changed in very recent SDK versions with
 * StripeClient, but the global-key style is still the documented approach
 * for 25.x and is what the webhook signature verification helper expects
 * to coexist with).
 */
@Configuration
public class StripeConfig {

    private final String apiKey;

    public StripeConfig(@Value("${stripe.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }
}
