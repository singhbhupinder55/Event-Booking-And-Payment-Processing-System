package com.portfolio.eventbooking.repository;

import com.portfolio.eventbooking.domain.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Loads the event row with a pessimistic write lock (SELECT ... FOR UPDATE
     * under MySQL/InnoDB). This is the mechanism that prevents overselling:
     * concurrent transactions attempting to book the same event will block
     * here until the holder commits or rolls back, serializing all seat
     * decrements for a given event.
     *
     * Deliberately NOT using optimistic locking (@Version-based retry) for
     * this path — under heavy contention for a small number of remaining
     * seats, optimistic locking would mean most of the 100 concurrent
     * requests fail with a version conflict and need client-side retry logic,
     * which pushes complexity to the caller and adds latency. A short-held
     * pessimistic lock on a single row is simpler to reason about and fast
     * enough at this scale. See README "Design Decisions" for the full tradeoff.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
