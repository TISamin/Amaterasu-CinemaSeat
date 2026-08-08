package com.cinemaseat.booking;

import com.cinemaseat.showseat.ShowSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Default implementation. Agent 2 must call this from the gateway callback handler.
 *
 * Concurrency: each transition is a conditional UPDATE filter on the current state,
 * so duplicate / raced callbacks reduce to a no-op rather than a second transition.
 */
@Service
public class BookingStateServiceImpl implements BookingStateService {

    private final BookingRepository bookings;
    private final ShowSeatRepository showSeats;

    public BookingStateServiceImpl(BookingRepository bookings,
                                   ShowSeatRepository showSeats) {
        this.bookings = bookings;
        this.showSeats = showSeats;
    }

    @Override
    @Transactional
    public void confirmBooking(String bookingRef) {
        Optional<Booking> opt = bookings.findByBookingRef(bookingRef);
        if (opt.isEmpty()) return;
        Booking b = opt.get();

        int updated = bookings.markConfirmed(bookingRef);
        if (updated == 0) {
            // Already CONFIRMED / FAILED / EXPIRED — duplicate callback; do nothing.
            return;
        }
        showSeats.confirmHold(b.getShowSeatId(), b.getUserId());
    }

    @Override
    @Transactional
    public void failPayment(String bookingRef) {
        Optional<Booking> opt = bookings.findByBookingRef(bookingRef);
        if (opt.isEmpty()) return;
        Booking b = opt.get();

        int updated = bookings.markPaymentFailed(bookingRef);
        if (updated == 0) return;
        showSeats.releaseHold(b.getShowSeatId(), b.getUserId());
    }

    @Override
    @Transactional
    public void expireBooking(String bookingRef) {
        Optional<Booking> opt = bookings.findByBookingRef(bookingRef);
        if (opt.isEmpty()) return;
        Booking b = opt.get();

        int updated = bookings.markExpired(bookingRef);
        if (updated == 0) return;
        showSeats.releaseHold(b.getShowSeatId(), b.getUserId());
    }
}