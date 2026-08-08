CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(64) NOT NULL UNIQUE,
    booking_ref VARCHAR(32) NOT NULL REFERENCES bookings(booking_ref),
    status VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'BDT',
    idempotency_key VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payments_payment_id ON payments(payment_id);
CREATE INDEX idx_payments_booking_ref ON payments(booking_ref);
