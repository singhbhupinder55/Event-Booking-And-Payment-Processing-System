package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Booking;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.domain.ProcessedStripeEvent;
import com.portfolio.eventbooking.repository.BookingRepository;
import com.portfolio.eventbooking.repository.EventRepository;
import com.portfolio.eventbooking.repository.ProcessedStripeEventRepository;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private ProcessedStripeEventRepository processedStripeEventRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private MetricsService metricsService;

    private StripeWebhookService service;

    @BeforeEach
    void setUp() {
        service = new StripeWebhookService(
            processedStripeEventRepository, bookingRepository, eventRepository, auditLogService, metricsService);
    }

    @Test
    void duplicateEventIdIsIgnoredWithoutTouchingBooking() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_123")).thenReturn(true);

        PaymentIntent intent = mock(PaymentIntent.class);
        boolean processed = service.processEvent("evt_123", "payment_intent.succeeded", intent);

        assertThat(processed).isFalse();
        verifyNoInteractions(bookingRepository);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void raceOnClaimInsertIsTreatedAsDuplicate() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_123")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        PaymentIntent intent = mock(PaymentIntent.class);
        boolean processed = service.processEvent("evt_123", "payment_intent.succeeded", intent);

        assertThat(processed).isFalse();
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void paymentSucceededConfirmsAPendingBooking() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_succeed")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = newBooking(1L, 5L, BookingStatus.PENDING, 2);
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getMetadata()).thenReturn(Map.of("bookingId", "1"));
        when(intent.getId()).thenReturn("pi_abc123");

        boolean processed = service.processEvent("evt_succeed", "payment_intent.succeeded", intent);

        assertThat(processed).isTrue();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(auditLogService).record(
            eq(1L), eq(5L), eq(BookingStatus.PENDING), eq(BookingStatus.CONFIRMED),
            eq(com.portfolio.eventbooking.domain.TriggerType.STRIPE_WEBHOOK), eq("evt_succeed"), any());
        // payment success never touches seat counts on the Event — seats
        // were already decremented at booking-creation time, not at payment time.
        verifyNoInteractions(eventRepository);
    }

    @Test
    void paymentSucceededIgnoredIfBookingAlreadyConfirmed() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_x")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = newBooking(1L, 5L, BookingStatus.CONFIRMED, 2); // already confirmed
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getMetadata()).thenReturn(Map.of("bookingId", "1"));

        service.processEvent("evt_x", "payment_intent.succeeded", intent);

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void paymentFailedCancelsBookingAndReleasesSeats() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_fail")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = newBooking(2L, 5L, BookingStatus.PENDING, 3);
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event event = new Event("Test", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600));
        event.reserveSeats(3); // simulate the seats this booking had already claimed
        when(eventRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getMetadata()).thenReturn(Map.of("bookingId", "2"));
        when(intent.getId()).thenReturn("pi_fail123");

        service.processEvent("evt_fail", "payment_intent.payment_failed", intent);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(event.getAvailableSeats()).isEqualTo(10); // released back to full capacity
        verify(auditLogService).record(
            eq(2L), eq(5L), eq(BookingStatus.PENDING), eq(BookingStatus.CANCELLED),
            eq(com.portfolio.eventbooking.domain.TriggerType.STRIPE_WEBHOOK), eq("evt_fail"), any());
    }

    @Test
    void chargeRefundedCancelsConfirmedBookingAndReleasesSeats() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_refund")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = newBooking(3L, 5L, BookingStatus.CONFIRMED, 1);
        when(bookingRepository.findByStripePaymentIntentId("pi_refund123")).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Event event = new Event("Test", "desc", 10, 5000L, "USD", Instant.now().plusSeconds(3600));
        event.reserveSeats(1);
        when(eventRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn("pi_refund123");

        service.processEvent("evt_refund", "charge.refunded", charge);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(event.getAvailableSeats()).isEqualTo(10);
    }

    @Test
    void refundIgnoredIfBookingNotConfirmed() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_refund2")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        Booking booking = newBooking(4L, 5L, BookingStatus.CANCELLED, 1); // already cancelled
        when(bookingRepository.findByStripePaymentIntentId("pi_refund456")).thenReturn(Optional.of(booking));

        Charge charge = mock(Charge.class);
        when(charge.getPaymentIntent()).thenReturn("pi_refund456");

        service.processEvent("evt_refund2", "charge.refunded", charge);

        verify(bookingRepository, never()).save(any());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void unknownEventTypeIsClaimedButHasNoEffect() {
        when(processedStripeEventRepository.existsByStripeEventId("evt_unknown")).thenReturn(false);
        when(processedStripeEventRepository.save(any(ProcessedStripeEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PaymentIntent intent = mock(PaymentIntent.class);
        boolean processed = service.processEvent("evt_unknown", "some.other.event", intent);

        assertThat(processed).isTrue(); // claimed successfully
        verifyNoInteractions(bookingRepository); // but no handler ran
    }

    private Booking newBooking(Long id, Long eventId, BookingStatus status, int seats) {
        Booking booking = new Booking(eventId, "user-1", seats, 5000L, "USD", Instant.now().plusSeconds(600));
        setId(booking, id);
        if (status != BookingStatus.PENDING) {
            // drive through valid transitions to reach the desired terminal/intermediate state for the test
            if (status == BookingStatus.CONFIRMED) {
                booking.transitionTo(BookingStatus.CONFIRMED);
            } else if (status == BookingStatus.CANCELLED) {
                booking.transitionTo(BookingStatus.CANCELLED);
            } else if (status == BookingStatus.EXPIRED) {
                booking.transitionTo(BookingStatus.EXPIRED);
            }
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
