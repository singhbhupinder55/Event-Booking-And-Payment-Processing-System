package com.portfolio.eventbooking.controller;

import com.portfolio.eventbooking.domain.Event;
import com.portfolio.eventbooking.dto.CreateEventRequest;
import com.portfolio.eventbooking.dto.EventResponse;
import com.portfolio.eventbooking.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event event = eventService.createEvent(
            request.name(), request.description(), request.totalCapacity(),
            request.priceCents(), request.currency(), request.startsAt());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<EventResponse> getEvent(@PathVariable Long eventId) {
        Event event = eventService.getEvent(eventId);
        return ResponseEntity.ok(EventResponse.from(event));
    }
}
