package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.IdempotencyKey;
import com.portfolio.eventbooking.exception.IdempotencyConflictException;
import com.portfolio.eventbooking.exception.IdempotencyInProgressException;
import com.portfolio.eventbooking.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    private IdempotencyService service;

    private static final String KEY = "client-key-123";
    private static final String PATH = "/api/events/1/bookings";
    private static final String BODY = "{\"userReference\":\"user-1\",\"seats\":2}";

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
    }

    @Test
    void firstRequestWithNewKeyProceeds() {
        when(repository.findByIdempotencyKeyAndRequestPath(KEY, PATH)).thenReturn(Optional.empty());
        when(repository.save(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyService.ClaimResult result = service.claimKey(KEY, PATH, BODY);

        assertThat(result).isInstanceOf(IdempotencyService.Proceed.class);
        verify(repository).save(any(IdempotencyKey.class));
    }

    @Test
    void completedKeyWithMatchingHashReplaysStoredResponse() throws Exception {
        IdempotencyKey existing = new IdempotencyKey(KEY, PATH, sha256(BODY));
        existing.complete(201, "{\"id\":42}");
        when(repository.findByIdempotencyKeyAndRequestPath(KEY, PATH)).thenReturn(Optional.of(existing));

        IdempotencyService.ClaimResult result = service.claimKey(KEY, PATH, BODY);

        assertThat(result).isInstanceOf(IdempotencyService.Replay.class);
        IdempotencyService.Replay replay = (IdempotencyService.Replay) result;
        assertThat(replay.responseStatus()).isEqualTo(201);
        assertThat(replay.responseBody()).isEqualTo("{\"id\":42}");
        verify(repository, never()).save(any());
    }

    @Test
    void completedKeyWithDifferentBodyThrowsConflict() throws Exception {
        IdempotencyKey existing = new IdempotencyKey(KEY, PATH, sha256("{\"different\":\"body\"}"));
        existing.complete(201, "{\"id\":42}");
        when(repository.findByIdempotencyKeyAndRequestPath(KEY, PATH)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.claimKey(KEY, PATH, BODY))
            .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void inProgressKeyWithMatchingHashThrowsInProgress() throws Exception {
        IdempotencyKey existing = new IdempotencyKey(KEY, PATH, sha256(BODY)); // never completed -> still IN_PROGRESS
        when(repository.findByIdempotencyKeyAndRequestPath(KEY, PATH)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.claimKey(KEY, PATH, BODY))
            .isInstanceOf(IdempotencyInProgressException.class);
    }

    @Test
    void raceOnInsertFallsBackToReReadingExistingRow() throws Exception {
        // Simulates two requests with the same key arriving close together:
        // the initial SELECT sees nothing, but the INSERT collides with a
        // row the other thread just committed.
        when(repository.findByIdempotencyKeyAndRequestPath(KEY, PATH))
            .thenReturn(Optional.empty()) // first check: nothing yet
            .thenReturn(Optional.of(completedRow())); // re-check after losing the race: now it exists
        when(repository.save(any(IdempotencyKey.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key"));

        IdempotencyService.ClaimResult result = service.claimKey(KEY, PATH, BODY);

        assertThat(result).isInstanceOf(IdempotencyService.Replay.class);
    }

    @Test
    void completeKeyPersistsResponseOnTheClaimedRow() {
        IdempotencyKey claimed = new IdempotencyKey(KEY, PATH, "somehash");
        setId(claimed, 7L);
        when(repository.findById(7L)).thenReturn(Optional.of(claimed));
        when(repository.save(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.completeKey(7L, 201, "{\"id\":1}");

        assertThat(claimed.getStatus()).isEqualTo(IdempotencyKey.Status.COMPLETED);
        assertThat(claimed.getResponseStatus()).isEqualTo(201);
        assertThat(claimed.getResponseBody()).isEqualTo("{\"id\":1}");
    }

    private IdempotencyKey completedRow() throws Exception {
        IdempotencyKey row = new IdempotencyKey(KEY, PATH, sha256(BODY));
        row.complete(201, "{\"id\":99}");
        return row;
    }

    /** Mirrors IdempotencyService's private sha256 method for test setup purposes. */
    private String sha256(String input) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** IdempotencyKey's id is JPA-generated and has no public setter; set it via reflection for this test only. */
    private void setId(IdempotencyKey key, Long id) {
        try {
            Field idField = IdempotencyKey.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(key, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
