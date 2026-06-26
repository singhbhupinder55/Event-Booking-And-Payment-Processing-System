package com.portfolio.eventbooking.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private Event newEvent(int capacity) {
        return new Event("Test Event", "desc", capacity, 5000L, "USD", Instant.now().plusSeconds(3600));
    }

    @Test
    void newEventStartsWithAvailableSeatsEqualToCapacity() {
        Event event = newEvent(50);
        assertThat(event.getAvailableSeats()).isEqualTo(50);
        assertThat(event.getTotalCapacity()).isEqualTo(50);
    }

    @Test
    void reserveSeatsDecrementsAvailability() {
        Event event = newEvent(50);
        event.reserveSeats(10);
        assertThat(event.getAvailableSeats()).isEqualTo(40);
    }

    @Test
    void reservingMoreThanAvailableThrows() {
        Event event = newEvent(5);
        assertThatThrownBy(() -> event.reserveSeats(6))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot reserve");
        // State must be unchanged after the failed attempt.
        assertThat(event.getAvailableSeats()).isEqualTo(5);
    }

    @Test
    void reservingExactlyAllRemainingSeatsSucceeds() {
        Event event = newEvent(5);
        event.reserveSeats(5);
        assertThat(event.getAvailableSeats()).isZero();
    }

    @Test
    void releaseSeatsIncrementsAvailability() {
        Event event = newEvent(50);
        event.reserveSeats(20);
        event.releaseSeats(5);
        assertThat(event.getAvailableSeats()).isEqualTo(35);
    }

    @Test
    void releasingMoreThanWasReservedThrowsRatherThanExceedingCapacity() {
        Event event = newEvent(10);
        event.reserveSeats(3);
        assertThatThrownBy(() -> event.releaseSeats(8)) // would bring available to 7+8=15 > capacity 10
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("exceed total capacity");
    }
}
