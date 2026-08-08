CREATE TABLE payment_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    payment_id VARCHAR(64) NOT NULL,
    booking_ref VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount INT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'BDT',
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_events_event_id ON payment_events(event_id);
CREATE INDEX idx_payment_events_payment_id ON payment_events(payment_id);
CREATE INDEX idx_payment_events_booking_ref ON payment_events(booking_ref);
