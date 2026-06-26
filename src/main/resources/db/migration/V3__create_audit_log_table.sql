CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    booking_id      BIGINT          NOT NULL,
    event_id        BIGINT          NOT NULL,
    from_status     VARCHAR(20),                 -- nullable: NULL for the initial creation event
    to_status       VARCHAR(20)     NOT NULL,
    trigger_type    VARCHAR(40)     NOT NULL,     -- e.g. USER_ACTION, STRIPE_WEBHOOK, SYSTEM_EXPIRY
    trigger_source  VARCHAR(255),                 -- e.g. stripe event id, user reference, "scheduler"
    correlation_id  VARCHAR(64)     NOT NULL,     -- ties this row back to one request lifecycle in the logs
    metadata        JSON,                         -- free-form extra context (e.g. stripe payload subset)
    created_at      DATETIME(6)     NOT NULL,

    CONSTRAINT fk_audit_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)

    -- Intentionally no updated_at / no UPDATE path in application code:
    -- this table is append-only by convention. We don't enforce that with a
    -- DB trigger here (would be overkill for portfolio scope) but it's called
    -- out explicitly in the README as a known simplification.
) ENGINE=InnoDB;

CREATE INDEX idx_audit_booking_id ON audit_log(booking_id);
CREATE INDEX idx_audit_correlation_id ON audit_log(correlation_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at);
