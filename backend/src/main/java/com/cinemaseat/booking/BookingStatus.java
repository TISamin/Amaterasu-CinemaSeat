package com.cinemaseat.booking;

/**
 * Mirrors docs/DATABASE_CONTRACT.md §12 and docs/STATE_MACHINE.md §7.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PAYMENT_FAILED,
    EXPIRED
}
