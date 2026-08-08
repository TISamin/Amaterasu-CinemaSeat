package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import com.cinemaseat.payment.gateway.GatewayChargeRequest;
import com.cinemaseat.payment.gateway.GatewayChargeResponse;
import com.cinemaseat.payment.gateway.GatewayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Owns the payment lifecycle:
 *
 * <ul>
 *   <li>{@link #pay(String)} — create the {@code PENDING} payment row using the
 *       gateway's payment_id, then forward to the gateway. Returns immediately;
 *       final state arrives via callback.</li>
 *   <li>{@link #handleCallback(GatewayCallbackPayload)} — idempotent log+apply.
 *       First time we see an {@code event_id}: insert {@code payment_events}
 *       and delegate to {@link BookingStateService}. Subsequent times:
 *       short-circuit and return.</li>
 * </ul>
 *
 * Race rule: gateway may send the callback before {@code /pay} returns. The
 * UNIQUE(event_id) on {@code payment_events} + conditional UPDATEs in
 * {@link BookingStateService} ensure correctness under any ordering.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final String CURRENCY = "BDT";

    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PaymentEventRepository events;
    private final ShowSeatRepository showSeats;
    private final GatewayClient gateway;
    private final BookingStateService bookingState;

    public PaymentService(BookingRepository bookings,
                          PaymentRepository payments,
                          PaymentEventRepository events,
                          ShowSeatRepository showSeats,
                          GatewayClient gateway,
                          BookingStateService bookingState) {
        this.bookings = bookings;
        this.payments = payments;
        this.events = events;
        this.showSeats = showSeats;
        this.gateway = gateway;
        this.bookingState = bookingState;
    }

    /**
     * Initiate payment for a booking that is currently {@code PENDING_PAYMENT}.
     * Always returns fast — the gateway callback decides final state.
     *
     * @return the freshly-created payment row, or the existing one if {@code /pay}
     *         was already called once for this booking (idempotent re-entry).
     */
    @Transactional
    public Payment pay(String bookingRef) {
        Booking booking = bookings.findByBookingRef(bookingRef)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown bookingRef: " + bookingRef));

        if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException(
                    "Booking " + bookingRef + " is not PENDING_PAYMENT (was "
                            + booking.getStatus() + ")");
        }

        // Idempotent re-entry: the same booking calling /pay twice is allowed.
        // We forward to the gateway again but do not duplicate the payment row.
        Optional<Payment> existing = payments.findByBookingRef(bookingRef);
        if (existing.isPresent()) {
            Payment p = existing.get();
            log.info("PAY_REENTRY bookingRef={} paymentId={} status={}",
                    bookingRef, p.getPaymentId(), p.getStatus());
            return p;
        }

        BigDecimal amount = lookupAmount(booking);
        GatewayChargeResponse resp = gateway.charge(new GatewayChargeRequest(
                bookingRef, amount, CURRENCY, "/api/payments/callback"));

        Payment row = new Payment();
        row.setPaymentId(resp.paymentId());
        row.setBookingRef(bookingRef);
        row.setStatus(PaymentStatus.PENDING);
        row.setAmount(amount);
        row.setCurrency(CURRENCY);
        try {
            payments.saveAndFlush(row);
        } catch (DataIntegrityViolationException dup) {
            // Another /pay won the race. Re-read the row they wrote.
            Payment winner = payments.findByBookingRef(bookingRef)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment row missing after dup-key race", dup));
            log.info("PAY_RACE_LOST bookingRef={} existingPaymentId={}",
                    bookingRef, winner.getPaymentId());
            return winner;
        }

        log.info("PAY_INITIATED bookingRef={} paymentId={} amount={}",
                bookingRef, resp.paymentId(), amount);
        return row;
    }

    /**
     * Process a gateway callback. Idempotent under concurrent or duplicate
     * delivery — see STATE_MACHINE.md §14 and DATABASE_CONTRACT §16.
     */
    @Transactional
    public void handleCallback(GatewayCallbackPayload payload) {
        if (payload.eventId() == null || payload.eventId().isBlank()) {
            log.warn("CALLBACK_MISSING_EVENT_ID bookingRef={} paymentId={}",
                    payload.bookingRef(), payload.paymentId());
            return; // never a 409 to the gateway
        }

        // Fast path: we already saw this event. Skip the INSERT attempt.
        Optional<PaymentEvent> existing = events.findByEventId(payload.eventId());
        if (existing.isPresent()) {
            log.info("CALLBACK_DUPLICATE eventId={} bookingRef={}",
                    payload.eventId(), payload.bookingRef());
            return;
        }

        // Log the event first. UNIQUE(event_id) is the canonical idempotency guard.
        PaymentEvent ev = new PaymentEvent();
        ev.setEventId(payload.eventId());
        ev.setPaymentId(payload.paymentId());
        ev.setBookingRef(payload.bookingRef());
        ev.setStatus(payload.status());
        ev.setAmount(payload.amount());
        ev.setCurrency(payload.currency());
        try {
            events.saveAndFlush(ev);
        } catch (DataIntegrityViolationException dup) {
            // Lost the INSERT race against a concurrent duplicate. Treat as dup.
            log.info("CALLBACK_DUPLICATE_VIA_RACE eventId={} bookingRef={}",
                    payload.eventId(), payload.bookingRef());
            return;
        }

        // Move payment row to terminal state.
        if (payload.paymentId() != null && !payload.paymentId().isBlank()) {
            String newStatus = switch (payload.status()) {
                case SUCCEEDED -> PaymentStatus.SUCCEEDED.name();
                case FAILED    -> PaymentStatus.FAILED.name();
                case REFUNDED  -> PaymentStatus.REFUNDED.name();
                default        -> PaymentStatus.FAILED.name();
            };
            payments.markTerminalIfPending(payload.paymentId(), newStatus);
        }

        // Apply the booking/seat transition. BookingStateService itself is
        // idempotent (conditional UPDATE filtered on PENDING_PAYMENT).
        switch (payload.status()) {
            case SUCCEEDED -> bookingState.confirmBooking(payload.bookingRef());
            case FAILED    -> bookingState.failPayment(payload.bookingRef());
            case REFUNDED  -> {
                // Per STATE_MACHINE §13: REFUNDED does NOT touch booking/seat in MVP.
                // Just update payment row above.
            }
            default -> log.warn("CALLBACK_UNKNOWN_STATUS status={} bookingRef={}",
                    payload.status(), payload.bookingRef());
        }

        log.info("CALLBACK_APPLIED eventId={} bookingRef={} status={} paymentId={}",
                payload.eventId(), payload.bookingRef(), payload.status(), payload.paymentId());
    }

    /**
     * Look up the booking's show_seat price so we send the gateway the same
     * amount the frontend saw. Falls back to {@code 0.00} when the seat is
     * missing (shouldn't happen for a PENDING_PAYMENT booking).
     */
    private BigDecimal lookupAmount(Booking booking) {
        if (booking.getShowSeatId() == null) {
            return BigDecimal.ZERO;
        }
        return showSeats.findById(booking.getShowSeatId())
                .map(ShowSeat::getPrice)
                .orElse(BigDecimal.ZERO);
    }

    /** Payload mirrored from the gateway callback contract (API_CONTRACT §8). */
    public record GatewayCallbackPayload(
            String eventId,
            String paymentId,
            String bookingRef,
            PaymentStatus status,
            BigDecimal amount,
            String currency,
            Instant timestamp
    ) {}
}