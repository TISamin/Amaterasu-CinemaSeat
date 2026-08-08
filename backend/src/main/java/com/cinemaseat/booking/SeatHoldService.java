package com.cinemaseat.booking;

import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.booking.BookingRefGenerator;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.booking.HoldResult;
import com.cinemaseat.config.HoldProperties;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Performs the atomic seat hold + booking creation in one transaction.
 *
 * Concurrency strategy:
 *   1. Atomic UPDATE on show_seats (DATABASE_CONTRACT §9). 0 rows changed ⇒ conflict.
 *   1. INSERT into bookings with status=PENDING_PAYMENT.
 *   1. UNIQUE(booking_ref) protects against extremely rare ref collisions.
 *
 * If the booking insert fails, the transaction rolls back and the seat is released
 * automatically — no half-state.
 */
@Service
public class SeatHoldService {

    private static final Logger log = LoggerFactory.getLogger(SeatHoldService.class);

    private final ShowSeatRepository showSeats;
    private final BookingRepository bookings;
    private final HoldProperties holdProperties;
    private final BookingStateService bookingStateService;

    public SeatHoldService(ShowSeatRepository showSeats,
                           BookingRepository bookings,
                           HoldProperties holdProperties,
                           BookingStateService bookingStateService) {
        this.showSeats = showSeats;
        this.bookings = bookings;
        this.holdProperties = holdProperties;
        this.bookingStateService = bookingStateService;
    }

    /**
     * @param showId    show id from the URL
     * @param showSeatId  show_seats.id from the URL
     * @param userId    user identifier from the request body
     */
    @Transactional
    public HoldResult hold(Long showId, Long showSeatId, String userId) {
        // 1. Verify the (show, show_seat) pair exists. If not, conflict.
        ShowSeat seat = showSeats.findByIdAndShowId(showSeatId, showId)
                .orElse(null);
        if (seat == null) {
            return HoldResult.conflict(showId, showSeatId,
                    "Show seat not found for this show.");
        }

        // 2. Atomic UPDATE. This is the concurrency primitive.
        Instant expiresAt = Instant.now().plus(holdProperties.getTtlSeconds(), ChronoUnit.SECONDS);
        int updated = showSeats.tryHold(showId, showSeatId, userId, expiresAt);
        if (updated == 0) {
            return HoldResult.conflict(showId, showSeatId,
                    "Seat is already held or booked.");
        }

        // 3. Insert booking row. Booking ref is unique — retry on the (very rare) collision.
        for (int attempt = 0; attempt < 5; attempt++) {
            String ref = BookingRefGenerator.generate();
            try {
                Booking b = new Booking();
                b.setBookingRef(ref);
                b.setShowSeatId(showSeatId);
                b.setUserId(userId);
                b.setStatus(BookingStatus.PENDING_PAYMENT);
                bookings.save(b);

                log.info("SEAT_HELD bookingRef={} showId={} showSeatId={} userId={} expiresAt={}",
                        ref, showId, showSeatId, userId, expiresAt);

                return HoldResult.ok(ref, showId, showSeatId, seat.getSeatId(),
                        expiresAt, seat.getPrice());
            } catch (DataIntegrityViolationException dup) {
                log.warn("BOOKING_REF_COLLISION ref={} attempt={}", ref, attempt);
                // retry with a new ref
            }
        }

        // Could not generate a unique ref in 5 tries — extremely unlikely.
        // Force the transaction to roll back so the seat is released.
        throw new IllegalStateException("Unable to generate a unique booking reference");
    }
}