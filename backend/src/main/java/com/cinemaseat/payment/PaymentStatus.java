package com.cinemaseat.payment;

/**
 * Payment lifecycle. Mirrors docs/DATABASE_CONTRACT.md §14 and
 * docs/STATE_MACHINE.md §11–§13.
 *
 * Transitions are gated by a conditional UPDATE in {@link PaymentRepository}
 * (PENDING → terminal only), so duplicate / raced callbacks reduce to no-ops.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
