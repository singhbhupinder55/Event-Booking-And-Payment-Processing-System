package com.portfolio.eventbooking.integration;

import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.dto.CreateBookingRequest;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the brief's idempotency requirement end-to-end over real HTTP and a
 * real database: a client retrying the exact same booking request with the
 * same Idempotency-Key must not create a second booking or double-decrement
 * seat availability, and must get back the original response.
 */
class BookingIdempotencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void duplicateRequestWithSameIdempotencyKeyDoesNotDoubleBook() throws com.fasterxml.jackson.core.JsonProcessingException {
        Event event = eventRepository.save(new Event(
            "Idempotency Test Event", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600)));

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        CreateBookingRequest body = new CreateBookingRequest("user-1", 2);
        HttpEntity<CreateBookingRequest> request = new HttpEntity<>(body, headers);

        String url = "/api/events/" + event.getId() + "/bookings";

        ResponseEntity<String> firstResponse = restTemplate.postForEntity(url, request, String.class);
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(url, request, String.class);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Compare parsed JSON content, not raw string equality — the
        // replayed response is reconstructed from stored bytes and may
        // differ in whitespace/key-ordering from the original while still
        // representing identical data. No real client should care about
        // formatting; what matters is that the data is the same booking
        // (same id), proving the second request did NOT create a new one.
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode firstJson = mapper.readTree(firstResponse.getBody());
        com.fasterxml.jackson.databind.JsonNode secondJson = mapper.readTree(secondResponse.getBody());
        assertThat(secondJson)
            .as("the replayed response must represent the exact same booking as the original")
            .isEqualTo(firstJson);

        long bookingCount = bookingRepository.findAll().stream()
            .filter(b -> b.getEventId().equals(event.getId()))
            .count();
        assertThat(bookingCount)
            .as("only ONE booking should exist despite two identical requests")
            .isEqualTo(1);

        Event reloaded = eventRepository.findById(event.getId()).orElseThrow();
        assertThat(reloaded.getAvailableSeats())
            .as("seats should only be decremented once (10 - 2 = 8), not twice")
            .isEqualTo(8);
    }

    @Test
    void sameKeyWithDifferentBodyReturnsConflict() {
        Event event = eventRepository.save(new Event(
            "Idempotency Conflict Test", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600)));

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        String url = "/api/events/" + event.getId() + "/bookings";

        HttpEntity<CreateBookingRequest> firstRequest =
            new HttpEntity<>(new CreateBookingRequest("user-1", 2), headers);
        ResponseEntity<String> firstResponse = restTemplate.postForEntity(url, firstRequest, String.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Same key, different body (different seat count) — must be rejected, not silently accepted.
        HttpEntity<CreateBookingRequest> secondRequest =
            new HttpEntity<>(new CreateBookingRequest("user-1", 3), headers);
        ResponseEntity<String> secondResponse = restTemplate.postForEntity(url, secondRequest, String.class);

        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void missingIdempotencyKeyHeaderIsRejected() {
        Event event = eventRepository.save(new Event(
            "Missing Header Test", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // deliberately no Idempotency-Key header

        HttpEntity<CreateBookingRequest> request =
            new HttpEntity<>(new CreateBookingRequest("user-1", 1), headers);
        String url = "/api/events/" + event.getId() + "/bookings";

        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
