package com.portfolio.eventbooking.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class BookingStatusTest {

    @Test
    void pendingCanTransitionToConfirmedCancelledOrExpired() {
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.PENDING.canTransitionTo(BookingStatus.EXPIRED)).isTrue();
    }

    @Test
    void confirmedCanOnlyTransitionToCancelled() {
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.PENDING)).isFalse();
        assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.EXPIRED)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CANCELLED", "EXPIRED"})
    void terminalStatesHaveNoOutgoingTransitions(BookingStatus terminal) {
        assertThat(terminal.isTerminal()).isTrue();
        assertThat(terminal.allowedTransitions()).isEmpty();
    }

    @Test
    void pendingAndConfirmedAreNotTerminal() {
        assertThat(BookingStatus.PENDING.isTerminal()).isFalse();
        assertThat(BookingStatus.CONFIRMED.isTerminal()).isFalse();
    }

    @Test
    void noStatusCanTransitionToItself() {
        for (BookingStatus status : BookingStatus.values()) {
            assertThat(status.canTransitionTo(status))
                .as("%s should not be able to transition to itself", status)
                .isFalse();
        }
    }
}
