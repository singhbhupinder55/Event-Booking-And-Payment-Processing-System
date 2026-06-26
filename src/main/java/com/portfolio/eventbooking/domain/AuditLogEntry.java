package com.portfolio.eventbooking.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/**
 * An immutable record of one booking state transition. By convention
 * (not DB-enforced — see README "Known Simplifications"), rows in this
 * table are only ever inserted, never updated or deleted. This is what
 * lets us answer "what happened to booking #4471" after the fact.
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private BookingStatus fromStatus; // nullable: null on the initial PENDING creation row

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private BookingStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 40)
    private TriggerType triggerType;

    @Column(name = "trigger_source")
    private String triggerSource;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLogEntry() {
        // JPA
    }

    public AuditLogEntry(Long bookingId, Long eventId, BookingStatus fromStatus, BookingStatus toStatus,
                          TriggerType triggerType, String triggerSource, String correlationId, String metadata) {
        this.bookingId = bookingId;
        this.eventId = eventId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.triggerType = triggerType;
        this.triggerSource = triggerSource;
        this.correlationId = correlationId;
        this.metadata = metadata;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public Long getEventId() {
        return eventId;
    }

    public BookingStatus getFromStatus() {
        return fromStatus;
    }

    public BookingStatus getToStatus() {
        return toStatus;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public String getTriggerSource() {
        return triggerSource;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
