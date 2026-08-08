package com.cinemaseat.payment;

/**
 * Thrown when a /pay request cannot proceed because the booking has no usable
 * amount/currency (H-2 hardening) or the existing pending payment row disagrees
 * with the booking's current price.
 */
public class PaymentValidationException extends RuntimeException {
    public PaymentValidationException(String message) { super(message); }
}