package com.portfolio.eventbooking.repository;

import com.portfolio.eventbooking.domain.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    /** Full history for one booking, oldest first — the "what happened to booking #4471" query. */
    List<AuditLogEntry> findByBookingIdOrderByCreatedAtAsc(Long bookingId);
}
