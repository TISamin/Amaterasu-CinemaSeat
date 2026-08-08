package com.cinemaseat.showseat;

/**
 * Mirrors docs/DATABASE_CONTRACT.md §7 and docs/STATE_MACHINE.md §1.
 * Persisted as VARCHAR for forward-compat — values must match the DB CHECK constraint.
 */
public enum SeatStatus {
    AVAILABLE,
    HELD,
    BOOKED
}
