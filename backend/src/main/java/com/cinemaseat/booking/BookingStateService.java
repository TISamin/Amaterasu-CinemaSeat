package com.cinemaseat.booking;

/**
 * Service interface Agent 2 will invoke from the payment callback handler.
 *
 * Every method must be safe to call concurrently and must be idempotent
 * (duplicate callbacks must not change state twice).
 */
public interface BookingStateService {

    /**
     * Transition triggered by a successful gateway callback for the given booking.
     * Sets {@code Booking=CONFIRMED} and {@code ShowSeat=BOOKED}. No-op if booking is
     * already in a terminal state.
     */
    void confirmBooking(String bookingRef);

    /**
     * Transition triggered by a failed gateway callback.
     * Sets {@code Booking=PAYMENT_FAILED} and releases the seat back to AVAILABLE.
     * No-op if the booking is already in a terminal state.
     */
    void failPayment(String bookingRef);

    /**
     * Transition triggered by hold expiration (lazy on hold, or by a cleanup job).
     * Sets {@code Booking=EXPIRED} and releases the seat.
     */
    void expireBooking(String bookingRef);
}