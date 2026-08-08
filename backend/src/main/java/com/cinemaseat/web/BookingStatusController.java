package com.cinemaseat.web;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.show.ShowSeatRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingStatusController {

    private final BookingRepository bookings;
    private final ShowSeatRepository showSeats;
    private final SeatRepository seats;

    public BookingStatusController(BookingRepository bookings,
                                   ShowSeatRepository showSeats,
                                   SeatRepository seats) {
        this.bookings = bookings;
        this.showSeats = showSeats;
        this.seats = seats;
    }

    @GetMapping("/{bookingRef}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getBooking(@PathVariable String bookingRef) {
        Booking b = bookings.findByBookingRef(bookingRef).orElse(null);
        if (b == null) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("BOOKING_NOT_FOUND", "No booking with that ref."));
        }

        var showSeat = showSeats.findById(b.getShowSeatId()).orElse(null);
        Long seatId = null;
        java.math.BigDecimal amount = null;
        java.time.Instant holdExpiresAt = null;
        if (showSeat != null) {
            seatId = showSeat.getSeatId();
            amount = showSeat.getPrice();
            holdExpiresAt = showSeat.getHoldExpiresAt();
        }

        // Compared to the API contract, seatId here is the seat definition id;
        // the show_seat id is exposed via id. We keep it simple for the hackathon.
        return ResponseEntity.ok(new BookingDto(
                b.getBookingRef(),
                b.getStatus(),
                /* paymentStatus */
                mapBookingStatusToPayment(b.getStatus()),
                showSeat == null ? null : showSeat.getShowId(),
                b.getShowSeatId(),
                seatId,
                amount,
                holdExpiresAt
        ));
    }

    /** Best-effort booking→payment mapping for the API contract; PaymentStatus is owned by Agent 2. */
    private String mapBookingStatusToPayment(com.cinemaseat.booking.BookingStatus s) {
        return switch (s) {
            case CONFIRMED      -> "SUCCEEDED";
            case PAYMENT_FAILED -> "FAILED";
            case EXPIRED        -> "EXPIRED";
            case PENDING_PAYMENT -> "PENDING";
        };
    }
}