package com.portfolio.eventbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Tracks idempotent execution of client-initiated, payment-affecting requests.
 * Scoped to (idempotencyKey, requestPath) — see V4 migration comment for why.
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_path", nullable = false, length = 500)
    private String requestPath;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "json")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected IdempotencyKey() {
        // JPA
    }

    public IdempotencyKey(String idempotencyKey, String requestPath, String requestHash) {
        this.idempotencyKey = idempotencyKey;
        this.requestPath = requestPath;
        this.requestHash = requestHash;
        this.status = Status.IN_PROGRESS;
        this.createdAt = Instant.now();
    }

    public void complete(int responseStatus, String responseBody) {
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.status = Status.COMPLETED;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
