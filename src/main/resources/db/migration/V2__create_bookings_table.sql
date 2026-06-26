CREATE TABLE bookings (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id                BIGINT          NOT NULL,
    user_reference          VARCHAR(255)    NOT NULL,  -- external user id; no auth system in scope for this portfolio project
    status                  VARCHAR(20)     NOT NULL,  -- PENDING, CONFIRMED, CANCELLED, EXPIRED
    seats_requested         INT             NOT NULL,
    amount_cents            BIGINT          NOT NULL,
    currency                VARCHAR(3)      NOT NULL DEFAULT 'USD',
    stripe_payment_intent_id VARCHAR(255),
    reservation_expires_at  DATETIME(6)     NOT NULL,
    created_at              DATETIME(6)     NOT NULL,
    updated_at              DATETIME(6)     NOT NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT fk_bookings_event FOREIGN KEY (event_id) REFERENCES events(id),
    CONSTRAINT chk_bookings_seats_positive CHECK (seats_requested > 0),
    CONSTRAINT chk_bookings_amount_nonneg CHECK (amount_cents >= 0),
    CONSTRAINT chk_bookings_status CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'EXPIRED'))
) ENGINE=InnoDB;

CREATE INDEX idx_bookings_event_id ON bookings(event_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_expires_at ON bookings(reservation_expires_at);
CREATE UNIQUE INDEX uq_bookings_payment_intent ON bookings(stripe_payment_intent_id);
