CREATE TABLE idempotency_keys (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(255)    NOT NULL,
    request_path    VARCHAR(500)    NOT NULL,     -- key is scoped per-endpoint: same key on two different endpoints is not a collision
    request_hash    VARCHAR(64)     NOT NULL,     -- SHA-256 of normalized request body; detects "same key, different body" misuse
    response_status INT,                          -- nullable while in-flight
    response_body   JSON,                         -- nullable while in-flight
    status          VARCHAR(20)     NOT NULL,     -- IN_PROGRESS, COMPLETED
    created_at      DATETIME(6)     NOT NULL,
    completed_at    DATETIME(6),

    CONSTRAINT chk_idem_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
) ENGINE=InnoDB;

-- Scoped uniqueness: (key, path) must be unique, not just key, so the same
-- idempotency key reused across different endpoints by a sloppy client
-- doesn't silently cross-wire responses.
CREATE UNIQUE INDEX uq_idem_key_path ON idempotency_keys(idempotency_key, request_path);
