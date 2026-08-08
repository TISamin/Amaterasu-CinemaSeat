package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.config.GatewayProperties;
import com.cinemaseat.gateway.GatewayClient;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PaymentService. Pure Mockito - no DB, no Spring, no HTTP.
 *
 * <p>Uses Mockito LENIENT strictness because each test only needs a subset of
 * the default wiring. Strict would flag unused stubs and force boilerplate.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock BookingRepository bookings;
    @Mock BookingStateService bookingStateService;
    @Mock ShowSeatRepository showSeats;
    @Mock PaymentRepository payments;
    @Mock PaymentEventRepository paymentEvents;
    @Mock GatewayClient gateway;
    @Mock PaymentReserveService reserveService;

    GatewayProperties gatewayProps;
    PaymentService svc;

    private static final String REF = "BK-abc";
    private static final Long SEAT = 42L;
    private static final String AMOUNT = "350.00";

    @BeforeEach
    void setUp() {
        gatewayProps = new GatewayProperties(
                "http://gateway:9000", 5000,
                "http://api:8080/api/payments/callback", "");
        svc = new PaymentService(bookings, bookingStateService, showSeats,
                payments, paymentEvents, gateway, gatewayProps, reserveService);

        // Default reserve() return: a fresh PENDING row (id=1, reused=false).
        lenient().when(reserveService.reserve(REF)).thenReturn(
                new PaymentService.ReserveResult(1L, PaymentService.idempotencyKeyFor(REF),
                        new BigDecimal(AMOUNT), "BDT", false));
    }

    // --------------------------------------------------------------- /pay

    @Test
    void payHappyPath() {
        when(gateway.charge(any(GatewayClient.ChargeRequest.class), anyString(), eq(null)))
                .thenReturn(new GatewayClient.ChargeResponse("pay-xyz", PaymentStatus.PENDING));

        PaymentService.PayResult res = svc.pay(REF);

        assertThat(res.bookingRef()).isEqualTo(REF);
        assertThat(res.paymentId()).isEqualTo("pay-xyz");
        assertThat(res.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.reused()).isFalse();
        verify(payments).assignGatewayPaymentId(1L, "pay-xyz");
    }

    @Test
    void payGatewayTimeoutDoesNotCrash() {
        when(gateway.charge(any(GatewayClient.ChargeRequest.class), anyString(), any()))
                .thenThrow(new GatewayClient.GatewayTimeoutException("timed out"));

        assertThatThrownBy(() -> svc.pay(REF))
                .isInstanceOf(GatewayClient.GatewayTimeoutException.class);

        // No payment_id was assigned because the gateway never replied.
        verify(payments, never()).assignGatewayPaymentId(anyLong(), anyString());
        verify(payments, never()).markStatus(anyLong(), anyString());
    }

    @Test
    void payDelegatesValidationToReserveService() {
        when(reserveService.reserve(REF))
                .thenThrow(new PaymentTerminalException("booking CONFIRMED, not PENDING_PAYMENT"));

        assertThatThrownBy(() -> svc.pay(REF))
                .isInstanceOf(PaymentTerminalException.class);
        verify(gateway, never()).charge(any(), anyString(), any());
    }

    @Test
    void payReusesExistingPendingRow() {
        when(reserveService.reserve(REF)).thenReturn(
                new PaymentService.ReserveResult(7L, PaymentService.idempotencyKeyFor(REF),
                        new BigDecimal(AMOUNT), "BDT", true));
        when(gateway.charge(any(), anyString(), any()))
                .thenReturn(new GatewayClient.ChargeResponse("pay-7", PaymentStatus.PENDING));

        PaymentService.PayResult res = svc.pay(REF);

        assertThat(res.reused()).isTrue();
        verify(payments).assignGatewayPaymentId(7L, "pay-7");
    }

    // ----------------------------------------------------------- /callback

    @Test
    void callbackSucceededTransitionsEverything() {
        when(paymentEvents.insertIfAbsent(anyString(), anyString(), eq(REF),
                eq("SUCCEEDED"), any(BigDecimal.class), eq("BDT"))).thenReturn(1);
        when(payments.findByPaymentId("pay-xyz")).thenReturn(Optional.of(stubPayment("pay-xyz")));
        when(payments.markStatus(anyLong(), eq("SUCCEEDED"))).thenReturn(1);
        when(bookings.findByBookingRef(REF)).thenReturn(Optional.of(stubBooking(REF, SEAT)));
        when(showSeats.findById(SEAT)).thenReturn(Optional.of(ShowSeat.forTest(SEAT, new BigDecimal(AMOUNT))));

        PaymentService.CallbackOutcome out = svc.handleCallback(stubCallback("SUCCEEDED"));

        assertThat(out.duplicate()).isFalse();
        assertThat(out.amountMismatch()).isFalse();
        assertThat(out.finalPaymentStatus()).isEqualTo("SUCCEEDED");
        verify(payments).markStatus(anyLong(), eq("SUCCEEDED"));
        verify(bookingStateService).confirmBooking(REF);
        verify(bookingStateService, never()).failPayment(anyString());
    }

    @Test
    void callbackDuplicateEventIsNoop() {
        when(paymentEvents.insertIfAbsent(anyString(), anyString(), eq(REF),
                anyString(), any(BigDecimal.class), eq("BDT"))).thenReturn(0);

        PaymentService.CallbackOutcome out = svc.handleCallback(stubCallback("SUCCEEDED"));

        assertThat(out.duplicate()).isTrue();
        verify(payments, never()).findByPaymentId(anyString());
        verify(payments, never()).markStatus(anyLong(), anyString());
        verify(bookingStateService, never()).confirmBooking(anyString());
    }

    @Test
    void callbackAmountMismatchDoesNotTransition() {
        when(paymentEvents.insertIfAbsent(anyString(), anyString(), eq(REF),
                anyString(), any(BigDecimal.class), eq("BDT"))).thenReturn(1);
        when(payments.findByPaymentId("pay-xyz")).thenReturn(Optional.of(stubPayment("pay-xyz")));
        when(bookings.findByBookingRef(REF)).thenReturn(Optional.of(stubBooking(REF, SEAT)));
        when(showSeats.findById(SEAT)).thenReturn(Optional.of(ShowSeat.forTest(SEAT, new BigDecimal(AMOUNT))));

        CallbackPayload payload = new CallbackPayload(
                "evt-1", "pay-xyz", REF, "SUCCEEDED",
                new BigDecimal("999.00"), "BDT", null);

        PaymentService.CallbackOutcome out = svc.handleCallback(payload);

        assertThat(out.amountMismatch()).isTrue();
        verify(payments, never()).markStatus(anyLong(), anyString());
        verify(bookingStateService, never()).confirmBooking(anyString());
    }

    @Test
    void callbackRaceUpsertsThenTransitions() {
        when(paymentEvents.insertIfAbsent(anyString(), anyString(), eq(REF),
                anyString(), any(BigDecimal.class), eq("BDT"))).thenReturn(1);
        when(payments.findByPaymentId("pay-xyz")).thenReturn(Optional.empty());
        when(payments.upsertPending(eq(REF), eq(new BigDecimal(AMOUNT)), eq("BDT"), anyString()))
                .thenReturn(99L);
        when(payments.findById(99L)).thenReturn(Optional.of(stubPaymentWithId(99L, "pay-xyz")));
        when(payments.markStatus(eq(99L), eq("SUCCEEDED"))).thenReturn(1);
        when(bookings.findByBookingRef(REF)).thenReturn(Optional.of(stubBooking(REF, SEAT)));
        when(showSeats.findById(SEAT)).thenReturn(Optional.of(ShowSeat.forTest(SEAT, new BigDecimal(AMOUNT))));

        PaymentService.CallbackOutcome out = svc.handleCallback(stubCallback("SUCCEEDED"));

        assertThat(out.duplicate()).isFalse();
        verify(payments).upsertPending(eq(REF), any(BigDecimal.class), eq("BDT"), anyString());
        verify(payments).markStatus(eq(99L), eq("SUCCEEDED"));
        verify(bookingStateService).confirmBooking(REF);
    }

    @Test
    void callbackRefundedHasNoBookingSideEffects() {
        when(paymentEvents.insertIfAbsent(anyString(), anyString(), eq(REF),
                eq("REFUNDED"), any(BigDecimal.class), eq("BDT"))).thenReturn(1);
        when(payments.findByPaymentId("pay-xyz")).thenReturn(Optional.of(stubPayment("pay-xyz")));
        when(payments.markStatus(anyLong(), eq("REFUNDED"))).thenReturn(1);
        when(bookings.findByBookingRef(REF)).thenReturn(Optional.of(stubBooking(REF, SEAT)));
        when(showSeats.findById(SEAT)).thenReturn(Optional.of(ShowSeat.forTest(SEAT, new BigDecimal(AMOUNT))));

        PaymentService.CallbackOutcome out = svc.handleCallback(stubCallback("REFUNDED"));

        assertThat(out.finalPaymentStatus()).isEqualTo("REFUNDED");
        verify(payments).markStatus(anyLong(), eq("REFUNDED"));
        verify(bookingStateService, never()).confirmBooking(anyString());
        verify(bookingStateService, never()).failPayment(anyString());
    }

    @Test
    void callbackFailedReleasesSeat() {
        when(paymentEvents.insertIfAbsent(anyString(), anyString(), eq(REF),
                eq("FAILED"), any(BigDecimal.class), eq("BDT"))).thenReturn(1);
        when(payments.findByPaymentId("pay-xyz")).thenReturn(Optional.of(stubPayment("pay-xyz")));
        when(payments.markStatus(anyLong(), eq("FAILED"))).thenReturn(1);
        when(bookings.findByBookingRef(REF)).thenReturn(Optional.of(stubBooking(REF, SEAT)));
        when(showSeats.findById(SEAT)).thenReturn(Optional.of(ShowSeat.forTest(SEAT, new BigDecimal(AMOUNT))));

        svc.handleCallback(stubCallback("FAILED"));

        verify(payments).markStatus(anyLong(), eq("FAILED"));
        verify(bookingStateService).failPayment(REF);
    }

    // ----------------------------------------------------------------- OTP

    @Test
    void otpSendPassesThrough() {
        GatewayClient.OtpSendResponse expected =
                new GatewayClient.OtpSendResponse(true, "session-1", null);
        when(gateway.otpSend("+8801700000000", REF)).thenReturn(expected);

        GatewayClient.OtpSendResponse got = svc.forwardOtpSend("+8801700000000", REF);
        assertThat(got).isEqualTo(expected);
    }

    @Test
    void otpVerifyPassesThrough() {
        when(gateway.otpVerify(REF, "1234"))
                .thenReturn(new GatewayClient.OtpVerifyResponse(true));

        assertThatCode(() -> svc.forwardOtpVerify(REF, "1234"))
                .doesNotThrowAnyException();
        verify(gateway, times(1)).otpVerify(REF, "1234");
    }

    // -------------------------------------------------------------- helpers

    private Payment stubPayment(String paymentId) {
        return stubPaymentWithId(1L, paymentId);
    }

    private Payment stubPaymentWithId(Long id, String paymentId) {
        return Payment.forTest(id, paymentId, REF, PaymentStatus.PENDING,
                new BigDecimal(AMOUNT), "BDT", PaymentService.idempotencyKeyFor(REF));
    }

    private Booking stubBooking(String ref, Long seatId) {
        Booking b = new Booking();
        b.setBookingRef(ref);
        b.setShowSeatId(seatId);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        return b;
    }

    private CallbackPayload stubCallback(String status) {
        return new CallbackPayload(
                "evt-" + status, "pay-xyz", REF, status,
                new BigDecimal(AMOUNT), "BDT", null);
    }
}