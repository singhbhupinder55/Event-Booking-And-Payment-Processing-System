package com.portfolio.eventbooking.controller;

import com.portfolio.eventbooking.service.StripeWebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests webhook signature verification specifically — the part of the
 * webhook flow that's a real security control, not just business logic.
 * Builds valid and deliberately-invalid Stripe-Signature headers by hand,
 * following Stripe's documented scheme (HMAC-SHA256 over
 * "{timestamp}.{payload}", hex-encoded, formatted as
 * "t={timestamp},v1={signature}") since the SDK itself only provides
 * verification, not signing — only Stripe's servers sign in production.
 */
@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerSignatureTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret_for_unit_tests";

    @Mock
    private StripeWebhookService stripeWebhookService;

    @Test
    void validSignatureIsAccepted() throws Exception {
        String payload = samplePayload();
        String signatureHeader = buildValidSignatureHeader(payload, WEBHOOK_SECRET);

        StripeWebhookController controller =
            new StripeWebhookController(stripeWebhookService, WEBHOOK_SECRET);

        ResponseEntity<String> response = controller.handleStripeWebhook(payload, signatureHeader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void invalidSignatureIsRejectedWithoutProcessing() {
        String payload = samplePayload();
        String tamperedSignatureHeader = "t=" + Instant.now().getEpochSecond() + ",v1=0000invalidSignature0000";

        StripeWebhookController controller =
            new StripeWebhookController(stripeWebhookService, WEBHOOK_SECRET);

        ResponseEntity<String> response = controller.handleStripeWebhook(payload, tamperedSignatureHeader);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(stripeWebhookService, never()).processEvent(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void signatureSignedWithWrongSecretIsRejected() throws Exception {
        String payload = samplePayload();
        // Signed correctly per the scheme, but with a DIFFERENT secret than
        // the controller is configured to verify against — simulates
        // someone trying to forge a webhook without knowing our real secret.
        String wrongSecretSignature = buildValidSignatureHeader(payload, "whsec_a_completely_different_secret");

        StripeWebhookController controller =
            new StripeWebhookController(stripeWebhookService, WEBHOOK_SECRET);

        ResponseEntity<String> response = controller.handleStripeWebhook(payload, wrongSecretSignature);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String samplePayload() {
        // A structurally complete Stripe event envelope, matching the real
        // shape Stripe sends (api_version, created, livemode, etc. are all
        // required for the SDK's deserializer to successfully resolve the
        // nested data.object into a typed PaymentIntent rather than failing
        // deserialization).
        return """
            {
              "id": "evt_test123",
              "object": "event",
              "api_version": "2024-06-20",
              "created": 1700000000,
              "livemode": false,
              "pending_webhooks": 1,
              "request": {"id": null, "idempotency_key": null},
              "type": "payment_intent.succeeded",
              "data": {
                "object": {
                  "id": "pi_test123",
                  "object": "payment_intent",
                  "amount": 5000,
                  "currency": "usd",
                  "status": "succeeded",
                  "metadata": {"bookingId": "1"}
                }
              }
            }""";
    }

    /**
     * Replicates Stripe's documented webhook signing scheme:
     * signed_payload = "{timestamp}.{payload}"
     * signature = HMAC-SHA256(signed_payload, secret), hex-encoded
     * header = "t={timestamp},v1={signature}"
     */
    private String buildValidSignatureHeader(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String signedPayload = timestamp + "." + payload;

        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = hmac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }

        return "t=" + timestamp + ",v1=" + hex;
    }
}
