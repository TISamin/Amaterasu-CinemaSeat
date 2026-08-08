-- Up Migration
CREATE TABLE IF NOT EXISTS payment_events (
    id           BIGSERIAL PRIMARY KEY,
    event_id     TEXT        NOT NULL,
    payment_id   TEXT        NOT NULL,
    booking_ref  TEXT        NOT NULL,
    status       TEXT        NOT NULL
                 CHECK (status IN ('SUCCEEDED', 'FAILED', 'REFUNDED')),
    amount       INTEGER     NOT NULL,
    currency     TEXT        NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT payment_events_event_id_unique UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS payment_events_payment_id_idx ON payment_events (payment_id);
CREATE INDEX IF NOT EXISTS payment_events_booking_ref_idx ON payment_events (booking_ref);

-- Down Migration
DROP INDEX IF EXISTS payment_events_booking_ref_idx;
DROP INDEX IF EXISTS payment_events_payment_id_idx;
DROP TABLE IF EXISTS payment_events;