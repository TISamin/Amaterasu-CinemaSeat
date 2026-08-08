package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.PayResponse;
import com.cinemaseat.payment.dto.RefundResponse;
import com.cinemaseat.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/bookings/{bookingRef}/pay")
    public ResponseEntity<?> initiatePayment(
            @PathVariable String bookingRef,
            @RequestHeader(value = "X-Mock-Force", required = false) String mockForce,
            @RequestHeader(value = "X-Mock-Mode", required = false) String mockMode
    ) {
        try {
            Payment payment = paymentService.initiatePayment(bookingRef, mockForce, mockMode);
            PayResponse response = new PayResponse(
                    payment.getBookingRef(),
                    payment.getPaymentId(),
                    payment.getStatus().name()
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("BOOKING_NOT_FOUND", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse("PAYMENT_ERROR", e.getMessage()));
        }
    }

    @PostMapping("/api/payments/callback")
    public ResponseEntity<?> onCallback(
            @RequestBody byte[] rawBodyBytes,
            @RequestHeader(value = "X-Signature", required = false) String signatureHeader
    ) {
        try {
            paymentService.processCallback(rawBodyBytes, signatureHeader);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Per spec §7 / §8: always return 2xx for callback processing to avoid gateway retries
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/api/bookings/{bookingRef}/refund")
    public ResponseEntity<?> refundPayment(
            @PathVariable String bookingRef,
            @RequestHeader(value = "X-Mock-Force", required = false) String mockForce
    ) {
        try {
            Payment payment = paymentService.initiatePayment(bookingRef);
            RefundResponse response = paymentService.initiateRefund(payment.getPaymentId(), mockForce);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse("REFUND_FAILED", e.getMessage()));
        }
    }
}
