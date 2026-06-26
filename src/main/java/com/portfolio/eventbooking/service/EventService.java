package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.exception.ResourceNotFoundException;
import com.portfolio.eventbooking.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Plain CRUD for events. No locking here — findByIdForUpdate (the
 * concurrency-critical path) lives in BookingService, which is the only
 * place that needs the pessimistic lock. Reading/creating events doesn't
 * contend with bookings the way decrementing availableSeats does.
 */
@Service
public class EventService {

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public Event createEvent(String name, String description, int totalCapacity,
                              long priceCents, String currency, Instant startsAt) {
        Event event = new Event(name, description, totalCapacity, priceCents, currency, startsAt);
        return eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Event getEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> ResourceNotFoundException.forEvent(eventId));
    }
}
