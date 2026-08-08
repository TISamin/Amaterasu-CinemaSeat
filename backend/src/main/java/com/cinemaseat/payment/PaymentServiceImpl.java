package com.cinemaseat.payment;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStateService;
import com.cinemaseat.payment.dto.CallbackPayload;
import com.cinemaseat.payment.dto.ChargeRequest;
import com.cinemaseat.payment.dto.ChargeResponse;
import com.cinemaseat.payment.dto.OtpSendRequest;
import com.cinemaseat.payment.dto.OtpSendResponse;
import com.cinemaseat.payment.dto.OtpVerifyRequest;
import com.cinemaseat.payment.dto.OtpVerifyResponse;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private final BookingRepository bookingRepository;
    private final ShowSeatRepository showSeatRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final GatewayClient gatewayClient;
    private final BookingStateService bookingStateService;
    private final String callbackUrl;

    public PaymentServiceImpl(BookingRepository bookingRepository,
                              ShowSeatRepository showSeatRepository,
                              PaymentRepository paymentRepository,
                              PaymentEventRepository paymentEventRepository,
                              GatewayClient gatewayClient,
                              BookingStateService bookingStateService,
                              @Value("${CALLBACK_URL:http://api:8080/api/payments/callback}") String callbackUrl) {
        this.bookingRepository = bookingRepository;
        this.showSeatRepository = showSeatRepository;
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.gatewayClient = gatewayClient;
        this.bookingStateService = bookingStateService;
        this.callbackUrl = callbackUrl;
    }

    @Override
    @Transactional
    public Payment initiatePayment(String bookingRef) {
        Booking booking = bookingRepository.findByBookingRef(bookingRef)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found: " + bookingRef));

        Optional<Payment> existing = paymentRepository.findByBookingRef(bookingRef);
        if (existing.isPresent()) {
            return existing.get();
        }

        int amount = 450;
        Optional<ShowSeat> showSeatOpt = showSeatRepository.findById(booking.getShowSeatId());
        if (showSeatOpt.isPresent() && showSeatOpt.get().getPrice() != null) {
            amount = showSeatOpt.get().getPrice().intValue();
        }

        String idempotencyKey = "ik_" + bookingRef;
        ChargeRequest chargeReq = new ChargeRequest(amount, "BDT", bookingRef, callbackUrl);

        ChargeResponse response = gatewayClient.charge(chargeReq, idempotencyKey);

        Payment payment = new Payment();
        String pId = (response != null && response.getPaymentId() != null)
                ? response.getPaymentId()
                : "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        payment.setPaymentId(pId);
        payment.setBookingRef(bookingRef);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(amount);
        payment.setCurrency("BDT");
        payment.setIdempotencyKey(idempotencyKey);

        return paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public boolean processCallback(CallbackPayload payload) {
        if (payload == null || payload.getEventId() == null || payload.getEventId().isBlank()) {
            throw new IllegalArgumentException("Missing event_id in callback payload");
        }

        String eventId = payload.getEventId();
        if (paymentEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate callback received for event_id={}. Returning HTTP 200 without second transition.", eventId);
            return true;
        }

        PaymentEvent event = new PaymentEvent();
        event.setEventId(eventId);
        event.setPaymentId(payload.getPaymentId() != null ? payload.getPaymentId() : "unknown");
        event.setBookingRef(payload.getBookingRef() != null ? payload.getBookingRef() : "unknown");
        event.setStatus(payload.getStatus() != null ? payload.getStatus() : "UNKNOWN");
        event.setAmount(payload.getAmount() != null ? payload.getAmount() : 0);
        event.setCurrency(payload.getCurrency() != null ? payload.getCurrency() : "BDT");
        paymentEventRepository.save(event);

        if (payload.getPaymentId() != null) {
            paymentRepository.findByPaymentId(payload.getPaymentId()).ifPresent(p -> {
                if ("SUCCEEDED".equalsIgnoreCase(payload.getStatus())) {
                    p.setStatus(PaymentStatus.SUCCEEDED);
                } else if ("FAILED".equalsIgnoreCase(payload.getStatus())) {
                    p.setStatus(PaymentStatus.FAILED);
                } else if ("REFUNDED".equalsIgnoreCase(payload.getStatus())) {
                    p.setStatus(PaymentStatus.REFUNDED);
                }
                paymentRepository.save(p);
            });
        }

        if (payload.getBookingRef() != null) {
            String status = payload.getStatus();
            if ("SUCCEEDED".equalsIgnoreCase(status)) {
                bookingStateService.confirmBooking(payload.getBookingRef());
            } else if ("FAILED".equalsIgnoreCase(status)) {
                bookingStateService.failPayment(payload.getBookingRef());
            }
        }

        return true;
    }

    @Override
    public OtpSendResponse sendOtp(OtpSendRequest req) {
        return gatewayClient.otpSend(req);
    }

    @Override
    public OtpVerifyResponse verifyOtp(OtpVerifyRequest req) {
        return gatewayClient.otpVerify(req);
    }
}
