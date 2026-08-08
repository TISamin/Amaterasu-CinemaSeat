-- Agent 2 (payment module) — V4.
-- Source of truth: docs/DATABASE_CONTRACT.md §15.
-- payment_events is the callback audit log; UNIQUE(event_id) is the duplicate-
-- callback dedup primitive. Insert must succeed exactly once per event.

SET TIME ZONE 'UTC';

CREATE TABLE payment_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     VARCHAR(128) NOT NULL,
    payment_id   VARCHAR(128),
    booking_ref  VARCHAR(64),
    status       VARCHAR(16)  NOT NULL,
    amount       NUMERIC(10,2),
    currency     VARCHAR(8),
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_events_event_id UNIQUE (event_id),
    CONSTRAINT ck_payment_events_status
        CHECK (status IN ('SUCCEEDED', 'FAILED', 'REFUNDED'))
);

CREATE INDEX idx_payment_events_payment_id  ON payment_events(payment_id);
CREATE INDEX idx_payment_events_booking_ref ON payment_events(booking_ref);
CREATE INDEX idx_payment_events_received_at ON payment_events(received_at);
