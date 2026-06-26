CREATE TABLE events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255)    NOT NULL,
    description     VARCHAR(2000),
    total_capacity  INT             NOT NULL,
    available_seats INT             NOT NULL,
    price_cents     BIGINT          NOT NULL,
    currency        VARCHAR(3)      NOT NULL DEFAULT 'USD',
    starts_at       DATETIME(6)     NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT chk_events_capacity_nonneg CHECK (total_capacity >= 0),
    CONSTRAINT chk_events_available_nonneg CHECK (available_seats >= 0),
    CONSTRAINT chk_events_price_nonneg CHECK (price_cents >= 0)
) ENGINE=InnoDB;

-- We deliberately do NOT rely on `version` (optimistic locking) for the seat
-- count under contention — see design notes in README. We use SELECT ... FOR UPDATE
-- (pessimistic) instead. The `version` column is kept for general entity-update
-- safety (e.g. admin edits to name/description) but is not the concurrency
-- control mechanism for booking.
