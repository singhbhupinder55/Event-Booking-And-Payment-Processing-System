-- Separate from idempotency_keys deliberately: this dedupes Stripe's webhook
-- deliveries (keyed on Stripe's own event id), which is a different problem
-- from deduping our API clients' retries. See README "Design Decisions".
CREATE TABLE processed_stripe_events (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    stripe_event_id     VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(100)    NOT NULL,
    booking_id          BIGINT,
    processed_at        DATETIME(6)     NOT NULL,

    CONSTRAINT fk_stripe_event_booking FOREIGN KEY (booking_id) REFERENCES bookings(id)
) ENGINE=InnoDB;

CREATE UNIQUE INDEX uq_stripe_event_id ON processed_stripe_events(stripe_event_id);
