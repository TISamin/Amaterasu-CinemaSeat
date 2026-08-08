-- Agent 2 — payment module.
-- Adds payments (gateway payment state) and payment_events (callback log).
-- See docs/DATABASE_CONTRACT.md §14 (payments) and §15 (payment_events).

SET TIME ZONE 'UTC';

CREATE TABLE payments (
    id            BIGSERIAL    PRIMARY KEY,
    payment_id    VARCHAR(128) NOT NULL,
    booking_ref   VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    amount        NUMERIC(10,2) NOT NULL,
    currency      VARCHAR(8)   NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payments_payment_id_unique UNIQUE (payment_id),
    CONSTRAINT payments_status_check
        CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED'))
);

CREATE TABLE payment_events (
    id            BIGSERIAL    PRIMARY KEY,
    event_id      VARCHAR(128) NOT NULL,
    payment_id    VARCHAR(128),
    booking_ref   VARCHAR(64)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    amount        NUMERIC(10,2),
    currency      VARCHAR(8),
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT payment_events_event_id_unique UNIQUE (event_id),
    CONSTRAINT payment_events_status_check
        CHECK (status IN ('SUCCEEDED','FAILED','REFUNDED'))
);

-- Indexes from DATABASE_CONTRACT §17.
CREATE INDEX idx_payments_booking_ref ON payments (booking_ref);
CREATE INDEX idx_payment_events_event_id ON payment_events (event_id);

-- Note: payments.booking_ref → bookings.booking_ref is logically a FK, but the
-- contract lets us use repository conventions. We add it explicitly to make the
-- integrity guarantee DB-enforced.
ALTER TABLE payments
    ADD CONSTRAINT payments_booking_ref_fk
    FOREIGN KEY (booking_ref) REFERENCES bookings (booking_ref);