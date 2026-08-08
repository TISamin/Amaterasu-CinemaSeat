package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.config.GatewayProperties;
import com.cinemaseat.gateway.GatewayClient;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Single owner of payment state changes.
 *
 * <p>All gateway interactions, all writes to payments / payment_events,
 * and all calls into BookingStateService go through here. The class is
 * deliberately explicit about transaction boundaries so the gateway HTTP call
 * is never inside an open DB transaction (STATE_MACHINE §15).
 *
 * <h2>/pay flow</h2>
 * <ol>
 *   <li>Validate booking + read price + upsert PENDING row, all in a SHORT DB
 *       tx that commits BEFORE the gateway call. The DB-side of this lives in
 *       PaymentReserveService so Spring's @Transactional actually fires
 *       (self-invocation would bypass the proxy).</li>
 *   <li>Call gateway /charge OUTSIDE the tx. If the gateway hangs, no DB locks
 *       are held.</li>
 *   <li>Persist the gateway's payment_id back onto our row.</li>
 *   <li>Return 202 + payment_id + PENDING.</li>
 * </ol>
 *
 * <h2>/payments/callback flow</h2>
 * <ol>
 *   <li>Insert a payment_events row keyed by event_id using
 *       ON CONFLICT DO NOTHING. If 0 rows inserted -> duplicate ->
 *       return 200, do nothing.</li>
 *   <li>Otherwise: look up the payments row by gateway payment_id. Validate
 *       amount/currency against the payment row AND the booking's seat price.
 *       On mismatch: record the event but do NOT transition state (H-1).</li>
 *   <li>Transition payments.status via the atomic conditional UPDATE. Then
 *       delegate the booking + seat transition to BookingStateService.</li>
 * </ol>
 *
 * <h2>Refund policy (H-7, locked)</h2>
 * <p>REFUNDED callback is a no-op against bookings/show_seats. STATE_MACHINE
 * §13 says "refund behavior must follow the project's explicitly implemented
 * refund policy"; our MVP policy is "no booking/seat side-effects".
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookings;
    private final BookingStateService bookingStateService;
    private final ShowSeatRepository showSeats;
    private final PaymentRepository payments;
    private final PaymentEventRepository paymentEvents;
    private final GatewayClient gateway;
    private final GatewayProperties gatewayProps;
    private final PaymentReserveService reserveService;

    public PaymentService(BookingRepository bookings,
                          BookingStateService bookingStateService,
                          ShowSeatRepository showSeats,
                          PaymentRepository payments,
                          PaymentEventRepository paymentEvents,
                          GatewayClient gateway,
                          GatewayProperties gatewayProps,
                          PaymentReserveService reserveService) {
        this.bookings = bookings;
        this.bookingStateService = bookingStateService;
        this.showSeats = showSeats;
        this.payments = payments;
        this.paymentEvents = paymentEvents;
        this.gateway = gateway;
        this.gatewayProps = gatewayProps;
        this.reserveService = reserveService;
    }

    // ===================================================================
    // /pay
    // ===================================================================

    public PayResult pay(String bookingRef) {
        return pay(bookingRef, null);
    }

    public PayResult pay(String bookingRef, String mockForce) {
        ReserveResult persisted = reserveService.reserve(bookingRef);

        GatewayClient.ChargeResponse charge;
        try {
            charge = gateway.charge(
                    new GatewayClient.ChargeRequest(
                            persisted.amount(),
                            persisted.currency(),
                            bookingRef,
                            gatewayProps.getCallbackUrl()),
                    persisted.idempotencyKey(),
                    mockForce);
        } catch (RuntimeException e) {
            log.warn("gateway /charge failed for booking={} : {}", bookingRef, e.toString());
            throw e;
        }

        payments.assignGatewayPaymentId(persisted.paymentDbId(), charge.paymentId());

        return new PayResult(bookingRef, charge.paymentId(), PaymentStatus.PENDING, persisted.reused());
    }

    public static String idempotencyKeyFor(String bookingRef) {
        return "pay:" + bookingRef + ":1";
    }

    // ===================================================================
    // OTP forwarding - pass-through to the gateway, no DB involvement.
    // ===================================================================

    public GatewayClient.OtpSendResponse forwardOtpSend(String phone, String ref) {
        return gateway.otpSend(phone, ref);
    }

    public GatewayClient.OtpVerifyResponse forwardOtpVerify(String ref, String code) {
        return gateway.otpVerify(ref, code);
    }

    // ===================================================================
    // /payments/callback
    // ===================================================================

    public record CallbackOutcome(
            boolean duplicate,
            boolean amountMismatch,
            String bookingRef,
            String paymentId,
            String finalPaymentStatus
    ) {}

    public record PayResult(String bookingRef, String paymentId, PaymentStatus status, boolean reused) {}

    public record ReserveResult(Long paymentDbId, String idempotencyKey,
                                BigDecimal amount, String currency, boolean reused) {}

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CallbackOutcome handleCallback(CallbackPayload payload) {
        Objects.requireNonNull(payload, "callback payload");
        Objects.requireNonNull(payload.event_id(), "event_id");
        Objects.requireNonNull(payload.payment_id(), "payment_id");
        Objects.requireNonNull(payload.booking_ref(), "booking_ref");
        if (!payload.statusIsOneOf("SUCCEEDED", "FAILED", "REFUNDED")) {
            throw new IllegalArgumentException("unsupported callback status: " + payload.status());
        }

        int inserted = paymentEvents.insertIfAbsent(
                payload.event_id(),
                payload.payment_id(),
                payload.booking_ref(),
                payload.status(),
                payload.amount(),
                payload.currency());
        if (inserted == 0) {
            log.info("duplicate callback event_id={} booking={}",
                    payload.event_id(), payload.booking_ref());
            return new CallbackOutcome(true, false, payload.booking_ref(),
                    payload.payment_id(), null);
        }

        Payment payment = payments.findByPaymentId(payload.payment_id()).orElse(null);
        if (payment == null) {
            Long id = payments.upsertPending(
                    payload.booking_ref(),
                    payload.amount(),
                    payload.currency(),
                    "race:" + payload.payment_id());
            payment = payments.findById(id).orElseThrow();
        }

        Booking booking = bookings.findByBookingRef(payload.booking_ref())
                .orElseThrow(() -> new PaymentValidationException(
                        "callback for unknown booking " + payload.booking_ref()));
        BigDecimal expectedAmount = expectedAmount(booking);
        if (expectedAmount != null
                && (payment.getAmount().compareTo(payload.amount()) != 0
                || !Objects.equals(payment.getCurrency(), payload.currency())
                || expectedAmount.compareTo(payload.amount()) != 0)) {
            log.warn("callback amount/currency mismatch event_id={} booking={} payload_amount={} expected={}",
                    payload.event_id(), payload.booking_ref(), payload.amount(), expectedAmount);
            return new CallbackOutcome(false, true, payload.booking_ref(),
                    payload.payment_id(), payment.getStatus().name());
        }

        int updated = switch (payload.status()) {
            case "SUCCEEDED" -> payments.markStatus(payment.getId(), "SUCCEEDED");
            case "FAILED"    -> payments.markStatus(payment.getId(), "FAILED");
            case "REFUNDED"  -> payments.markStatus(payment.getId(), "REFUNDED");
            default -> 0;
        };
        if (updated == 0) {
            return new CallbackOutcome(false, false, payload.booking_ref(),
                    payment.getPaymentId(), payment.getStatus().name());
        }

        switch (payload.status()) {
            case "SUCCEEDED" -> bookingStateService.confirmBooking(payload.booking_ref());
            case "FAILED"    -> bookingStateService.failPayment(payload.booking_ref());
            case "REFUNDED"  -> applyRefunded(payload.booking_ref());
        }

        return new CallbackOutcome(false, false, payload.booking_ref(),
                payload.payment_id(), payload.status());
    }

    private void applyRefunded(String bookingRef) {
        log.info("REFUNDED callback received for booking={} - MVP policy: no booking/seat side-effects", bookingRef);
    }

    private BigDecimal expectedAmount(Booking b) {
        if (b.getShowSeatId() == null) return null;
        return showSeats.findById(b.getShowSeatId()).map(ShowSeat::getPrice).orElse(null);
    }
}