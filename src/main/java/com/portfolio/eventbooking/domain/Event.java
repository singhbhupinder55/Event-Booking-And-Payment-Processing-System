package com.portfolio.eventbooking.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @Column(length = 2000)
    private String description;

    @Min(0)
    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Min(0)
    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Min(0)
    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Event() {
        // JPA
    }

    public Event(String name, String description, int totalCapacity, long priceCents, String currency, Instant startsAt) {
        this.name = name;
        this.description = description;
        this.totalCapacity = totalCapacity;
        this.availableSeats = totalCapacity;
        this.priceCents = priceCents;
        this.currency = currency;
        this.startsAt = startsAt;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Decrements available seats. Caller (the service layer, via
     * SELECT ... FOR UPDATE) is responsible for ensuring this is only
     * invoked while holding the pessimistic lock on this row — this method
     * itself just enforces the invariant that seats can never go negative.
     */
    public void reserveSeats(int seats) {
        if (seats > this.availableSeats) {
            throw new IllegalStateException(
                "Cannot reserve %d seats: only %d available for event %d".formatted(seats, availableSeats, id));
        }
        this.availableSeats -= seats;
        this.updatedAt = Instant.now();
    }

    /** Releases seats back to the pool (cancellation, expiry, refund). */
    public void releaseSeats(int seats) {
        int newAvailable = this.availableSeats + seats;
        if (newAvailable > this.totalCapacity) {
            throw new IllegalStateException(
                "Cannot release %d seats: would exceed total capacity %d for event %d".formatted(seats, totalCapacity, id));
        }
        this.availableSeats = newAvailable;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
