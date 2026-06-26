package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests the transactional per-booking expiry logic in isolation. The
 * @Scheduled sweep loop itself (which calls this service once per
 * candidate booking) is tested separately in
 * ReservationExpirySchedulerTest, since that's a different bean with a
 * different responsibility — see ReservationExpiryService's Javadoc for
 * why the sweep loop and the per-booking transactional logic live in two
 * separate beans.
 */
@ExtendWith(MockitoExtension.class)
class ReservationExpiryServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private MetricsService metricsService;

    private ReservationExpiryService service;

    @BeforeEach
    void setUp() {
        service = new ReservationExpiryService(bookingRepository, eventRepository, auditLogService, metricsService);
    }

    @Test
    void expiresAPendingBookingPastItsWindowAndReleasesSeats() {
        Booking booking = newBooking(1L, 10L, BookingStatus.PENDING, 2);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event event = new Event("Test", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600));
        event.reserveSeats(2);
        when(eventRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean expired = service.expireOneBooking(1L, "corr-1");

        assertThat(expired).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.EXPIRED);
        assertThat(event.getAvailableSeats()).isEqualTo(10);
        verify(auditLogService).record(
            eq(1L), eq(10L), eq(BookingStatus.PENDING), eq(BookingStatus.EXPIRED),
            eq(com.portfolio.eventbooking.domain.TriggerType.SYSTEM_EXPIRY), eq("scheduler"), any());
    }

    @Test
    void skipsBookingThatIsNoLongerPendingByTheTimeItIsProcessed() {
        // Simulates a payment webhook confirming the booking in the gap
        // between the sweep's query and this method actually running.
        Booking booking = newBooking(2L, 10L, BookingStatus.CONFIRMED, 1);
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(booking));

        boolean expired = service.expireOneBooking(2L, "corr-2");

        assertThat(expired).isFalse();
        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void skipsSilentlyIfBookingNoLongerExists() {
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        boolean expired = service.expireOneBooking(99L, "corr-3");

        assertThat(expired).isFalse();
        verifyNoInteractions(eventRepository);
    }

    private Booking newBooking(Long id, Long eventId, BookingStatus status, int seats) {
        Booking booking = new Booking(eventId, "user-1", seats, 5000L, "USD", Instant.now().minusSeconds(60));
        setId(booking, id);
        if (status == BookingStatus.CONFIRMED) {
            booking.transitionTo(BookingStatus.CONFIRMED);
        } else if (status == BookingStatus.CANCELLED) {
            booking.transitionTo(BookingStatus.CANCELLED);
        } else if (status == BookingStatus.EXPIRED) {
            booking.transitionTo(BookingStatus.EXPIRED);
        }
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
