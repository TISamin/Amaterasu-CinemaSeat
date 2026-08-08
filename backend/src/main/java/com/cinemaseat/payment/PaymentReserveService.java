package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * DB-only reserve step for /pay. Lives in its own bean so that
 * {@link Transactional @Transactional} actually applies when {@link PaymentService}
 * calls it — Spring's proxy cannot intercept self-invocations within the same
 * class, so the @Transactional-on-a-protected-method pattern is broken without
 * this split.
 */
@Component
public class PaymentReserveService {

    private final BookingRepository bookings;
    private final ShowSeatRepository showSeats;
    private final PaymentRepository payments;

    public PaymentReserveService(BookingRepository bookings,
                                 ShowSeatRepository showSeats,
                                 PaymentRepository payments) {
        this.bookings = bookings;
        this.showSeats = showSeats;
        this.payments = payments;
    }

    @Transactional
    public PaymentService.ReserveResult reserve(String bookingRef) {
        Booking b = bookings.findByBookingRef(bookingRef)
                .orElseThrow(() -> new PaymentNotFoundException(bookingRef));
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new PaymentTerminalException(
                    "booking " + bookingRef + " is " + b.getStatus() + ", not PENDING_PAYMENT");
        }

        ShowSeat seat = showSeats.findById(b.getShowSeatId())
                .orElseThrow(() -> new PaymentValidationException(
                        "booking " + bookingRef + " has no associated show_seat row"));
        BigDecimal amount = seat.getPrice();
        if (amount == null || amount.signum() <= 0) {
            throw new PaymentValidationException(
                    "booking " + bookingRef + " has invalid price: " + amount);
        }
        String currency = "BDT";

        String idemKey = PaymentService.idempotencyKeyFor(bookingRef);

        Optional<Payment> existing = payments.findByIdempotencyKey(idemKey);
        if (existing.isPresent()) {
            Payment p = existing.get();
            if (p.getStatus().isTerminal()) {
                throw new PaymentTerminalException(
                        "payment for booking " + bookingRef + " already terminal: " + p.getStatus());
            }
            if (p.getAmount().compareTo(amount) != 0
                    || !Objects.equals(p.getCurrency(), currency)) {
                throw new PaymentValidationException(
                        "existing pending payment amount/currency mismatch");
            }
            return new PaymentService.ReserveResult(p.getId(), idemKey, amount, currency, true);
        }

        Long id = payments.upsertPending(bookingRef, amount, currency, idemKey);
        return new PaymentService.ReserveResult(id, idemKey, amount, currency, false);
    }
}