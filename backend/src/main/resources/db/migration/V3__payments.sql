-- Agent 2 (payment module) — V3.
-- Source of truth: docs/DATABASE_CONTRACT.md §14.
-- Spring Boot / Java 21 stack. Postgres is the source of truth for payment state.

SET TIME ZONE 'UTC';

-- ---------------------------------------------------------------------------
-- payments
-- One row per payment attempt for a booking. payment_id is the gateway's id
-- (assigned asynchronously, so it can be NULL initially). idempotency_key is
-- what we send to /charge as the Idempotency-Key header — stable per booking
-- so retries don't create a second payment.
-- ---------------------------------------------------------------------------
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      VARCHAR(128),                       -- gateway-side id, may be NULL until /charge returns
    booking_ref     VARCHAR(64)  NOT NULL REFERENCES bookings(booking_ref) ON DELETE RESTRICT,
    status          VARCHAR(16)  NOT NULL,
    amount          NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    currency        VARCHAR(8)   NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payments_payment_id      UNIQUE (payment_id),
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ck_payments_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_payments_booking_ref ON payments(booking_ref);
CREATE INDEX idx_payments_status      ON payments(status);

-- Only one non-terminal payment per booking at a time. A successful or terminal
-- payment (CONFIRMED/FAILED/REFUNDED) does not block retry attempts (the booking
-- is already in a terminal state by then and the API rejects /pay).
CREATE UNIQUE INDEX uq_payments_booking_pending
    ON payments(booking_ref)
    WHERE status = 'PENDING';
