package com.portfolio.eventbooking.controller;

import com.portfolio.eventbooking.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Receives Stripe webhook deliveries.
 *
 * SIGNATURE VERIFICATION IS NOT OPTIONAL: anyone who finds this URL could
 * POST a fake "payment_intent.succeeded" body claiming any booking was
 * paid for, if we didn't verify the Stripe-Signature header against our
 * webhook signing secret. Webhook.constructEvent does this verification
 * (HMAC-SHA256 over the raw payload using the signing secret, with a
 * timestamp check to reject stale/replayed signed payloads) and throws
 * SignatureVerificationException if it fails — we return 400 in that case
 * and never even look at the claimed event contents.
 *
 * We always return 2xx once an event is durably claimed/processed (even
 * for event types we don't have a handler for) so Stripe stops retrying
 * delivery. We return non-2xx ONLY for signature failures or unexpected
 * server errors, which Stripe's dashboard surfaces and which DOES trigger
 * Stripe's own retry-with-backoff behavior — exactly what we want for a
 * transient failure on our end.
 */
@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeWebhookService stripeWebhookService;
    private final String webhookSigningSecret;

    public StripeWebhookController(StripeWebhookService stripeWebhookService,
                                    @Value("${stripe.webhook-secret}") String webhookSigningSecret) {
        this.stripeWebhookService = stripeWebhookService;
        this.webhookSigningSecret = webhookSigningSecret;
    }

    @PostMapping("/api/webhooks/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String rawPayload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {

        Event event;
        try {
            event = Webhook.constructEvent(rawPayload, signatureHeader, webhookSigningSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        Optional<StripeObject> dataObject = event.getDataObjectDeserializer().getObject();
        if (dataObject.isEmpty()) {
            // This happens when the event was serialized with a newer Stripe
            // API version than this SDK understands. Per Stripe's own docs,
            // the safe response is still 2xx (so Stripe doesn't retry
            // forever for an event we structurally can't parse), just logged
            // loudly so it's visible rather than silently dropped.
            log.error("Could not deserialize Stripe event data object: eventId={} type={} apiVersion={}",
                event.getId(), event.getType(), event.getApiVersion());
            return ResponseEntity.ok("Event received but could not be deserialized");
        }

        boolean processed = stripeWebhookService.processEvent(event.getId(), event.getType(), dataObject.get());
        return ResponseEntity.ok(processed ? "Processed" : "Duplicate, already processed");
    }
}
