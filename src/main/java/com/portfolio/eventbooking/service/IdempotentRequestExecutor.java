package com.portfolio.eventbooking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolio.eventbooking.domain.IdempotencyKey;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Orchestrates the full idempotency lifecycle around a controller action:
 * claim the key, run the supplied business logic if this is genuinely the
 * first attempt, persist the response for future replays, and translate a
 * stored replay straight back into a ResponseEntity without re-running
 * anything.
 *
 * Lives at the service layer (not as a Spring MVC interceptor/filter)
 * deliberately: an interceptor would need to buffer and re-parse the
 * servlet response stream to capture what the controller produced, which
 * is fiddly and harder to unit test. Wrapping the controller's own call
 * explicitly is more code at each call site but is simple to read, simple
 * to test in isolation, and keeps the response-serialization format
 * (whatever Jackson produces for the DTO) as the single source of truth for
 * what gets replayed later.
 */
@Component
public class IdempotentRequestExecutor {

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public IdempotentRequestExecutor(IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    /**
     * @param idempotencyKey the client-supplied Idempotency-Key header value
     * @param requestPath    the endpoint path, used to scope the key (see V4 migration)
     * @param requestBody    the raw request DTO, serialized internally to compute the request hash
     * @param successStatus  the HTTP status to report on a fresh (non-replayed) success
     * @param action         the actual business logic to run on a genuinely new request
     */
    public <T> ResponseEntity<?> execute(String idempotencyKey, String requestPath, Object requestBody,
                                          HttpStatus successStatus, Supplier<T> action) {
        String serializedRequest = toJson(requestBody);

        IdempotencyService.ClaimResult claim =
            idempotencyService.claimKey(idempotencyKey, requestPath, serializedRequest);

        if (claim instanceof IdempotencyService.Replay replay) {
            // Re-serve the exact response body captured the first time this
            // key was used. The stored body is ALREADY a JSON string, so we
            // return it as raw bytes with an explicit content type — this
            // unambiguously bypasses Jackson's message converter, which
            // would otherwise double-encode an already-serialized JSON
            // string (wrapping it in an extra layer of quotes).
            return ResponseEntity.status(replay.responseStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(replay.responseBody().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        IdempotencyKey claimedRow = ((IdempotencyService.Proceed) claim).claimedRow();
        T result = action.get();
        String serializedResponse = toJson(result);

        idempotencyService.completeKey(claimedRow.getId(), successStatus.value(), serializedResponse);

        return ResponseEntity.status(successStatus).body(result);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize value for idempotency tracking", e);
        }
    }
}
