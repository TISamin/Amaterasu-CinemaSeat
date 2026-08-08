package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.payment.PaymentService.GatewayCallbackPayload;
import com.cinemaseat.payment.gateway.GatewayChargeResponse;
import com.cinemaseat.payment.gateway.GatewayClient;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests for {@link PaymentService}. Verifies:
 * <ul>
 *   <li>{@code pay()} creates a {@code PENDING} row and calls the gateway.</li>
 *   <li>Re-entry on the same booking returns the existing row (idempotent).</li>
 *   <li>Callback delegates to {@link BookingStateService} on first event.</li>
 *   <li>Duplicate callback is a no-op (no double transition).</li>
 *   <li>Race-loser INSERT on {@code payment_events} is treated as duplicate.</li>
 *   <li>Refund callback updates payment row only, leaves booking alone.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock BookingRepository bookings;
    @Mock PaymentRepository payments;
    @Mock PaymentEventRepository events;
    @Mock ShowSeatRepository showSeats;
    @Mock GatewayClient gateway;
    @Mock BookingStateService bookingState;

    @InjectMocks PaymentService svc;

    private Booking booking;

    @BeforeEach
    void setUp() {
        booking = new Booking();
        booking.setBookingRef("BK-abc");
        booking.setShowSeatId(42L);
        booking.setUserId("alice");
        booking.setStatus(BookingStatus.PENDING_PAYMENT);
    }

    // ----- pay() -------------------------------------------------------------

    @Test
    void payCreatesPendingRowAndCallsGateway() {
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(payments.findByBookingRef("BK-abc")).thenReturn(Optional.empty());
        ShowSeat seat = mockShowSeat(new BigDecimal("450.00"));
        when(showSeats.findById(42L)).thenReturn(Optional.of(seat));
        when(gateway.charge(any())).thenReturn(new GatewayChargeResponse("pay_xyz", "PENDING"));

        Payment result = svc.pay("BK-abc");

        assertThat(result.getBookingRef()).isEqualTo("BK-abc");
        assertThat(result.getPaymentId()).isEqualTo("pay_xyz");
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getAmount()).isEqualByComparingTo("450.00");
        assertThat(result.getCurrency()).isEqualTo("BDT");

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPaymentId()).isEqualTo("pay_xyz");
    }

    @Test
    void payReentryReturnsExistingRowWithoutCharging() {
        Payment existing = new Payment();
        existing.setPaymentId("pay_first");
        existing.setBookingRef("BK-abc");
        existing.setStatus(PaymentStatus.PENDING);
        existing.setAmount(new BigDecimal("450.00"));
        existing.setCurrency("BDT");

        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(payments.findByBookingRef("BK-abc")).thenReturn(Optional.of(existing));

        Payment result = svc.pay("BK-abc");

        assertThat(result).isSameAs(existing);
        verify(gateway, never()).charge(any());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void payRejectsNonPendingBooking() {
        booking.setStatus(BookingStatus.CONFIRMED);
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> svc.pay("BK-abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING_PAYMENT");
        verify(gateway, never()).charge(any());
        verify(payments, never()).saveAndFlush(any());
    }

    @Test
    void payRaceLoserReturnsExistingRow() {
        when(bookings.findByBookingRef("BK-abc")).thenReturn(Optional.of(booking));
        when(payments.findByBookingRef("BK-abc"))
                .thenReturn(Optional.empty())                                // first lookup
                .thenReturn(Optional.of(existing("pay_winner")));            // after dup-key
        when(showSeats.findById(42L)).thenReturn(Optional.of(mockShowSeat(new BigDecimal("450.00"))));
        when(gateway.charge(any())).thenReturn(new GatewayChargeResponse("pay_loser", "PENDING"));
        when(payments.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        Payment result = svc.pay("BK-abc");

        assertThat(result.getPaymentId()).isEqualTo("pay_winner");
    }

    // ----- handleCallback() --------------------------------------------------

    @Test
    void firstCallbackInsertsEventAndDelegates() {
        when(events.findByEventId("evt_1")).thenReturn(Optional.empty());
        when(events.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GatewayCallbackPayload payload = new GatewayCallbackPayload(
                "evt_1", "pay_xyz", "BK-abc",
                PaymentStatus.SUCCEEDED, new BigDecimal("450.00"), "BDT",
                Instant.parse("2026-08-08T11:03:22.418Z"));

        svc.handleCallback(payload);

        verify(bookingState).confirmBooking("BK-abc");
        verify(bookingState, never()).failPayment(any());
        verify(payments).markTerminalIfPending("pay_xyz", "SUCCEEDED");
    }

    @Test
    void duplicateCallbackByLookupIsNoop() {
        when(events.findByEventId("evt_dup")).thenReturn(Optional.of(new PaymentEvent()));

        GatewayCallbackPayload payload = new GatewayCallbackPayload(
                "evt_dup", "pay_xyz", "BK-abc",
                PaymentStatus.SUCCEEDED, null, null, null);

        svc.handleCallback(payload);

        verify(events, never()).saveAndFlush(any());
        verify(bookingState, never()).confirmBooking(any());
        verify(payments, never()).markTerminalIfPending(any(), any());
    }

    @Test
    void duplicateCallbackByRaceIsNoop() {
        when(events.findByEventId("evt_race")).thenReturn(Optional.empty());
        when(events.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

        GatewayCallbackPayload payload = new GatewayCallbackPayload(
                "evt_race", "pay_xyz", "BK-abc",
                PaymentStatus.SUCCEEDED, null, null, null);

        svc.handleCallback(payload); // must not throw

        verify(bookingState, never()).confirmBooking(any());
        verify(payments, never()).markTerminalIfPending(any(), any());
    }

    @Test
    void failedCallbackReleasesSeatViaFailPayment() {
        when(events.findByEventId("evt_fail")).thenReturn(Optional.empty());
        when(events.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GatewayCallbackPayload payload = new GatewayCallbackPayload(
                "evt_fail", "pay_xyz", "BK-abc",
                PaymentStatus.FAILED, new BigDecimal("450.00"), "BDT", null);

        svc.handleCallback(payload);

        verify(bookingState).failPayment("BK-abc");
        verify(bookingState, never()).confirmBooking(any());
        verify(payments).markTerminalIfPending("pay_xyz", "FAILED");
    }

    @Test
    void refundedCallbackDoesNotTouchBooking() {
        when(events.findByEventId("evt_refund")).thenReturn(Optional.empty());
        when(events.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        GatewayCallbackPayload payload = new GatewayCallbackPayload(
                "evt_refund", "pay_xyz", "BK-abc",
                PaymentStatus.REFUNDED, new BigDecimal("450.00"), "BDT", null);

        svc.handleCallback(payload);

        verify(payments).markTerminalIfPending("pay_xyz", "REFUNDED");
        verify(bookingState, never()).confirmBooking(any());
        verify(bookingState, never()).failPayment(any());
    }

    @Test
    void callbackMissingEventIdIsIgnored() {
        GatewayCallbackPayload payload = new GatewayCallbackPayload(
                null, "pay_xyz", "BK-abc",
                PaymentStatus.SUCCEEDED, null, null, null);

        svc.handleCallback(payload);

        verify(events, never()).saveAndFlush(any());
        verify(bookingState, never()).confirmBooking(any());
    }

    // ----- helpers -----------------------------------------------------------

    private static Payment existing(String paymentId) {
        Payment p = new Payment();
        p.setPaymentId(paymentId);
        p.setBookingRef("BK-abc");
        p.setStatus(PaymentStatus.PENDING);
        p.setAmount(new BigDecimal("450.00"));
        p.setCurrency("BDT");
        return p;
    }

    private static ShowSeat mockShowSeat(BigDecimal price) {
        // ShowSeat has private fields with no setters, so we can't construct a
        // populated one normally. Use ReflectionTestUtils to set `price`
        // directly. The other fields stay null, which is fine — lookupAmount()
        // only reads price.
        ShowSeat s = new ShowSeat();
        ReflectionTestUtils.setField(s, "price", price);
        return s;
    }
}