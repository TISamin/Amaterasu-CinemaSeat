package com.cinemaseat.payment;

/** /pay requested for a booking_ref that does not exist. */
public class PaymentNotFoundException extends RuntimeException {
    private final String bookingRef;
    public PaymentNotFoundException(String bookingRef) {
        super("booking not found: " + bookingRef);
        this.bookingRef = bookingRef;
    }
    public String getBookingRef() { return bookingRef; }
}