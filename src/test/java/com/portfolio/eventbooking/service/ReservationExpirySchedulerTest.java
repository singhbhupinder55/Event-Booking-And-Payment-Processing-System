package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests the sweep loop's orchestration logic: does it call the service
 * once per candidate, does one failure stop it from processing the rest,
 * does an empty candidate list short-circuit cleanly. The actual
 * transactional expiry logic is mocked out entirely here (tested for real
 * in ReservationExpiryServiceTest) since this class's only job is the loop
 * itself.
 */
@ExtendWith(MockitoExtension.class)
class ReservationExpirySchedulerTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ReservationExpiryService reservationExpiryService;

    private ReservationExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ReservationExpiryScheduler(bookingRepository, reservationExpiryService);
    }

    @Test
    void emptySweepDoesNothing() {
        when(bookingRepository.findByStatusAndReservationExpiresAtBefore(eq(BookingStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of());

        scheduler.sweepExpiredReservations();

        verifyNoInteractions(reservationExpiryService);
    }

    @Test
    void sweepCallsServiceOnceForEachCandidate() {
        Booking b1 = newBooking(1L);
        Booking b2 = newBooking(2L);
        Booking b3 = newBooking(3L);

        when(bookingRepository.findByStatusAndReservationExpiresAtBefore(eq(BookingStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(b1, b2, b3));
        when(reservationExpiryService.expireOneBooking(any(), anyString())).thenReturn(true);

        scheduler.sweepExpiredReservations();

        verify(reservationExpiryService).expireOneBooking(eq(1L), anyString());
        verify(reservationExpiryService).expireOneBooking(eq(2L), anyString());
        verify(reservationExpiryService).expireOneBooking(eq(3L), anyString());
    }

    @Test
    void sweepContinuesProcessingRemainingCandidatesAfterOneThrows() {
        Booking b1 = newBooking(1L);
        Booking b2 = newBooking(2L);
        Booking b3 = newBooking(3L);

        when(bookingRepository.findByStatusAndReservationExpiresAtBefore(eq(BookingStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(b1, b2, b3));
        when(reservationExpiryService.expireOneBooking(eq(1L), anyString())).thenReturn(true);
        when(reservationExpiryService.expireOneBooking(eq(2L), anyString()))
            .thenThrow(new RuntimeException("simulated failure processing booking 2"));
        when(reservationExpiryService.expireOneBooking(eq(3L), anyString())).thenReturn(true);

        // Must not throw despite booking 2 failing — the sweep itself
        // should swallow the per-booking exception and keep going.
        scheduler.sweepExpiredReservations();

        verify(reservationExpiryService).expireOneBooking(eq(1L), anyString());
        verify(reservationExpiryService).expireOneBooking(eq(2L), anyString());
        verify(reservationExpiryService).expireOneBooking(eq(3L), anyString()); // still reached despite #2 throwing
    }

    @Test
    void allCandidatesShareTheSameCorrelationIdForOneSweepRun() {
        Booking b1 = newBooking(1L);
        Booking b2 = newBooking(2L);

        when(bookingRepository.findByStatusAndReservationExpiresAtBefore(eq(BookingStatus.PENDING), any(Instant.class)))
            .thenReturn(List.of(b1, b2));
        when(reservationExpiryService.expireOneBooking(any(), anyString())).thenReturn(true);

        scheduler.sweepExpiredReservations();

        org.mockito.ArgumentCaptor<String> correlationIdCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(reservationExpiryService, times(2)).expireOneBooking(any(), correlationIdCaptor.capture());

        List<String> capturedIds = correlationIdCaptor.getAllValues();
        org.assertj.core.api.Assertions.assertThat(capturedIds.get(0)).isEqualTo(capturedIds.get(1));
    }

    private Booking newBooking(Long id) {
        Booking booking = new Booking(10L, "user-1", 1, 5000L, "USD", Instant.now().minusSeconds(60));
        setId(booking, id);
        return booking;
    }

    private void setId(Booking booking, Long id) {
        try {
            Field idField = Booking.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(booking, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
