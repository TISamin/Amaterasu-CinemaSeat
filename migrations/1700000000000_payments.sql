-- Up Migration
CREATE TABLE IF NOT EXISTS payments (
    id              BIGSERIAL PRIMARY KEY,
    payment_id      TEXT        NOT NULL,
    booking_ref     TEXT        NOT NULL,
    status          TEXT        NOT NULL
                    CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'REFUNDED')),
    amount          INTEGER     NOT NULL,
    currency        TEXT        NOT NULL,
    idempotency_key TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT payments_payment_id_unique UNIQUE (payment_id),
    CONSTRAINT payments_idempotency_key_unique UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS payments_booking_ref_idx ON payments (booking_ref);
CREATE INDEX IF NOT EXISTS payments_status_idx ON payments (status);

-- Down Migration
DROP INDEX IF EXISTS payments_status_idx;
DROP INDEX IF EXISTS payments_booking_ref_idx;
DROP TABLE IF EXISTS payments;