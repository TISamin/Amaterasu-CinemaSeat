package com.cinemaseat.booking;

import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import com.cinemaseat.web.BookingDto;
import com.cinemaseat.web.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Read-only query service for bookings.
 *
 * Hydrates the booking + show_seat + (optional) seat into the
 * {@link BookingDto} the API contract exposes, including the
 * booking→payment status mapping.
 *
 * Note: write-side booking state changes go through
 * {@link BookingStateService}, not this class.
 */
@Service
public class BookingQueryService {

    private final BookingRepository bookings;
    private final ShowSeatRepository showSeats;
    private final SeatRepository seats;

    public BookingQueryService(BookingRepository bookings,
                               ShowSeatRepository showSeats,
                               SeatRepository seats) {
        this.bookings = bookings;
        this.showSeats = showSeats;
        this.seats = seats;
    }

    /**
     * Look up a booking by its public ref.
     *
     * @return a {@code 200} response with the {@link BookingDto}, or a
     *         {@code 404} response with {@link ErrorResponse} when no
     *         booking exists for that ref.
     */
    @Transactional(readOnly = true)
    public ResponseEntity<?> getByRef(String bookingRef) {
        Booking b = bookings.findByBookingRef(bookingRef).orElse(null);
        if (b == null) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("BOOKING_NOT_FOUND", "No booking with that ref."));
        }
        return ResponseEntity.ok(toDto(b));
    }

    private BookingDto toDto(Booking b) {
        ShowSeat showSeat = showSeats.findById(b.getShowSeatId()).orElse(null);
        Long seatId = null;
        BigDecimal amount = null;
        Instant holdExpiresAt = null;
        if (showSeat != null) {
            seatId = showSeat.getSeatId();
            amount = showSeat.getPrice();
            holdExpiresAt = showSeat.getHoldExpiresAt();
        }
        return new BookingDto(
                b.getBookingRef(),
                b.getStatus(),
                mapBookingStatusToPayment(b.getStatus()),
                showSeat == null ? null : showSeat.getShowId(),
                b.getShowSeatId(),
                seatId,
                amount,
                holdExpiresAt
        );
    }

    /**
     * Best-effort booking→payment mapping for the API contract;
     * {@code PaymentStatus} is owned by Agent 2's payment module.
     */
    private String mapBookingStatusToPayment(BookingStatus s) {
        return switch (s) {
            case CONFIRMED       -> "SUCCEEDED";
            case PAYMENT_FAILED  -> "FAILED";
            case EXPIRED         -> "EXPIRED";
            case PENDING_PAYMENT -> "PENDING";
        };
    }

    /** Package-private convenience for tests/inspection. */
    Optional<Booking> findRaw(String bookingRef) {
        return bookings.findByBookingRef(bookingRef);
    }
}