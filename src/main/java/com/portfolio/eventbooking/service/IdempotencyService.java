package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.IdempotencyKey;
import com.portfolio.eventbooking.exception.IdempotencyConflictException;
import com.portfolio.eventbooking.exception.IdempotencyInProgressException;
import com.portfolio.eventbooking.repository.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * Implements idempotent execution for client-initiated, payment-affecting
 * requests, per the brief: "store a record of (idempotency key -> request
 * hash -> response) so a retried request with the same key returns the
 * original result instead of re-executing."
 *
 * Concurrency note: the "claim a key" step (claimKey) relies on the unique
 * index on (idempotency_key, request_path) — see V4 migration — to make the
 * claim atomic even if two requests with the same key arrive at almost the
 * same instant. We don't take an explicit lock here; we let the database's
 * unique constraint be the arbiter, and catch the resulting
 * DataIntegrityViolationException as the signal that we lost the race.
 * This is a different concurrency mechanism than the pessimistic
 * SELECT ... FOR UPDATE used in BookingService — that one protects a
 * counter that's read-then-written, this one protects a row that's
 * either freshly inserted or already exists, which a unique constraint
 * handles more simply than a row lock would.
 */
@Service
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    /**
     * Represents the outcome of attempting to claim an idempotency key
     * before running the underlying business logic.
     */
    public sealed interface ClaimResult permits Proceed, Replay {
    }

    /** No prior record exists for this key+path — caller should run the real logic. */
    public record Proceed(IdempotencyKey claimedRow) implements ClaimResult {
    }

    /** A completed prior record exists with a matching request hash — return its stored response. */
    public record Replay(int responseStatus, String responseBody) implements ClaimResult {
    }

    /**
     * Attempts to claim the given idempotency key for the given request path
     * and body. Must run in its own transaction (REQUIRES_NEW) so that the
     * claim is committed (and visible to other concurrent requests) even
     * though the caller's own business-logic transaction hasn't committed
     * yet — otherwise a concurrent duplicate request wouldn't see this
     * IN_PROGRESS row until we're already done, defeating the purpose.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClaimResult claimKey(String idempotencyKey, String requestPath, String requestBody) {
        String requestHash = sha256(requestBody);

        Optional<IdempotencyKey> existing =
            idempotencyKeyRepository.findByIdempotencyKeyAndRequestPath(idempotencyKey, requestPath);

        if (existing.isPresent()) {
            return handleExisting(existing.get(), requestHash);
        }

        try {
            IdempotencyKey claimed = idempotencyKeyRepository.save(
                new IdempotencyKey(idempotencyKey, requestPath, requestHash));
            return new Proceed(claimed);
        } catch (DataIntegrityViolationException raceLost) {
            // Another request claimed this exact key+path between our SELECT
            // and our INSERT. Re-read and resolve the same way we would have
            // if we'd seen it on the first lookup.
            IdempotencyKey existingAfterRace = idempotencyKeyRepository
                .findByIdempotencyKeyAndRequestPath(idempotencyKey, requestPath)
                .orElseThrow(() -> raceLost); // should not happen: the row must exist if our insert collided with it
            return handleExisting(existingAfterRace, requestHash);
        }
    }

    private ClaimResult handleExisting(IdempotencyKey existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(existing.getIdempotencyKey());
        }
        if (existing.getStatus() == IdempotencyKey.Status.IN_PROGRESS) {
            throw new IdempotencyInProgressException(existing.getIdempotencyKey());
        }
        return new Replay(existing.getResponseStatus(), existing.getResponseBody());
    }

    /**
     * Records the final response against a previously claimed key, so future
     * retries can replay it. Runs in its own transaction for the same reason
     * claimKey does — we want this committed and visible immediately,
     * independent of whatever transaction the caller's business logic used.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeKey(Long idempotencyRowId, int responseStatus, String responseBody) {
        IdempotencyKey key = idempotencyKeyRepository.findById(idempotencyRowId)
            .orElseThrow(() -> new IllegalStateException("Idempotency key row vanished: " + idempotencyRowId));
        key.complete(responseStatus, responseBody);
        idempotencyKeyRepository.save(key);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JVM; this is unreachable in practice.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
