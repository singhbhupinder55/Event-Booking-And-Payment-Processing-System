package com.portfolio.eventbooking.service;

import com.portfolio.eventbooking.config.CorrelationIdHolder;
import com.portfolio.eventbooking.domain.AuditLogEntry;
import com.portfolio.eventbooking.domain.BookingStatus;
import com.portfolio.eventbooking.domain.TriggerType;
import com.portfolio.eventbooking.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

/**
 * Single entry point for writing audit_log rows. Every booking state
 * transition in the system — whether triggered by a user action, a Stripe
 * webhook, or the expiry sweep — calls this so there's exactly one place
 * that decides how an audit row is shaped, instead of each caller building
 * AuditLogEntry objects by hand.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void record(Long bookingId, Long eventId, BookingStatus from, BookingStatus to,
                        TriggerType triggerType, String triggerSource, String metadataJson) {
        String correlationId = resolveCorrelationId();
        AuditLogEntry entry = new AuditLogEntry(
            bookingId, eventId, from, to, triggerType, triggerSource, correlationId, metadataJson);
        auditLogRepository.save(entry);
    }

    /**
     * Falls back to a fresh ID if called outside any request/job context that
     * set one (defensive — should not normally happen, but an audit row with
     * a generated fallback ID is far better than one that fails to write or
     * NPEs because no correlation ID was set).
     */
    private String resolveCorrelationId() {
        String current = CorrelationIdHolder.get();
        return (current != null) ? current : "unset-" + java.util.UUID.randomUUID();
    }
}
