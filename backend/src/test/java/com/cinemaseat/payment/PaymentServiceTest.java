package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.payment.dto.CallbackPayload;
import com.cinemaseat.payment.dto.ChargeRequest;
import com.cinemaseat.payment.dto.ChargeResponse;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock BookingRepository bookingRepository;
    @Mock ShowSeatRepository showSeatRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentEventRepository paymentEventRepository;
    @Mock GatewayClient gatewayClient;
    @Mock BookingStateService bookingStateService;

    private PaymentServiceImpl paymentService;

    private Booking booking;
    private ShowSeat showSeat;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentServiceImpl(
                bookingRepository,
                showSeatRepository,
                paymentRepository,
                paymentEventRepository,
                gatewayClient,
                bookingStateService,
                "http://api:8080/api/payments/callback"
        );

        booking = new Booking();
        booking.setBookingRef("BK-100");
        booking.setShowSeatId(501L);
        booking.setUserId("user-001");

        showSeat = new ShowSeat();
        showSeat.setPrice(new BigDecimal("450"));
    }

    @Test
    void initiatePaymentSuccess() {
        when(bookingRepository.findByBookingRef("BK-100")).thenReturn(Optional.of(booking));
        when(paymentRepository.findByBookingRef("BK-100")).thenReturn(Optional.empty());
        when(showSeatRepository.findById(501L)).thenReturn(Optional.of(showSeat));
        when(gatewayClient.charge(any(ChargeRequest.class), eq("ik_BK-100")))
                .thenReturn(new ChargeResponse("pay_123", "PENDING"));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment p = paymentService.initiatePayment("BK-100");

        assertThat(p.getPaymentId()).isEqualTo("pay_123");
        assertThat(p.getBookingRef()).isEqualTo("BK-100");
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(p.getAmount()).isEqualTo(450);

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void processCallbackSuccess() {
        CallbackPayload payload = new CallbackPayload("evt_1", "pay_123", "BK-100", "SUCCEEDED", 450, "BDT", "2026-08-08T12:00:00Z");

        when(paymentEventRepository.existsByEventId("evt_1")).thenReturn(false);
        when(paymentRepository.findByPaymentId("pay_123")).thenReturn(Optional.empty());

        boolean res = paymentService.processCallback(payload);

        assertThat(res).isTrue();
        verify(paymentEventRepository).save(any(PaymentEvent.class));
        verify(bookingStateService).confirmBooking("BK-100");
    }

    @Test
    void processDuplicateCallbackReturns200WithoutSecondTransition() {
        CallbackPayload payload = new CallbackPayload("evt_1", "pay_123", "BK-100", "SUCCEEDED", 450, "BDT", "2026-08-08T12:00:00Z");

        when(paymentEventRepository.existsByEventId("evt_1")).thenReturn(true);

        boolean res = paymentService.processCallback(payload);

        assertThat(res).isTrue();
        verify(paymentEventRepository, never()).save(any(PaymentEvent.class));
        verify(bookingStateService, never()).confirmBooking(any());
        verify(bookingStateService, never()).failPayment(any());
    }
}
