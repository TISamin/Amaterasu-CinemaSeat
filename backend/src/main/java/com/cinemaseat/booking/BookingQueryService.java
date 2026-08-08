package com.cinemaseat.booking;

import com.cinemaseat.payment.Payment;
import com.cinemaseat.payment.PaymentRepository;
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
 * BookingDto the API contract exposes, including the
 * booking->payment status mapping.
 *
 * Note: write-side booking state changes go through
 * BookingStateService, not this class.
 */
@Service
public class BookingQueryService {

    private final BookingRepository bookings;
    private final ShowSeatRepository showSeats;
    private final SeatRepository seats;
    private final PaymentRepository payments;

    public BookingQueryService(BookingRepository bookings,
                               ShowSeatRepository showSeats,
                               SeatRepository seats,
                               PaymentRepository payments) {
        this.bookings = bookings;
        this.showSeats = showSeats;
        this.seats = seats;
        this.payments = payments;
    }

    /**
     * Look up a booking by its public ref.
     *
     * @return 200 with the BookingDto, or 404 with ErrorResponse when no
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
                resolvePaymentStatus(b),
                showSeat == null ? null : showSeat.getShowId(),
                b.getShowSeatId(),
                seatId,
                amount,
                holdExpiresAt
        );
    }

    /**
     * Prefer the real Payment.status from the most recent payment row
     * for this booking. Fall back to the booking-status heuristic when no
     * payment row exists yet (e.g. bookings created before /pay was called).
     */
    private String resolvePaymentStatus(Booking b) {
        Optional<Payment> latest = payments.findLatestByBookingRef(b.getBookingRef());
        if (latest.isPresent()) {
            return latest.get().getStatus().name();
        }
        return mapBookingStatusToPayment(b.getStatus());
    }

    /**
     * Best-effort booking->payment mapping for the API contract
     * used only when no payment row has been created yet.
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