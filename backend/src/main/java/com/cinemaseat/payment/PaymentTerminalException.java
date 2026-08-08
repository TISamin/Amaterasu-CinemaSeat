package com.cinemaseat.payment;

/**
 * /pay rejected because the booking is already in a terminal state
 * (CONFIRMED / PAYMENT_FAILED / EXPIRED) or the most recent payment row is
 * already terminal. The caller should surface a 409.
 */
public class PaymentTerminalException extends RuntimeException {
    public PaymentTerminalException(String message) { super(message); }
}