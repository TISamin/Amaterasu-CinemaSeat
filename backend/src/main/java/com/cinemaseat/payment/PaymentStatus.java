package com.cinemaseat.payment;

/**
 * Lifecycle of a payment. See docs/STATE_MACHINE.md §11.
 */
public enum PaymentStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REFUNDED
}