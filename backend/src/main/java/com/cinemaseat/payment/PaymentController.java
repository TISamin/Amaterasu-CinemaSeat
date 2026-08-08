package com.cinemaseat.payment;

import com.cinemaseat.payment.dto.CallbackPayload;
import com.cinemaseat.payment.dto.PayResponse;
import com.cinemaseat.web.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/api/bookings/{bookingRef}/pay")
    public ResponseEntity<?> initiatePayment(@PathVariable String bookingRef) {
        try {
            Payment payment = paymentService.initiatePayment(bookingRef);
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
    public ResponseEntity<?> onCallback(@RequestBody CallbackPayload payload) {
        try {
            paymentService.processCallback(payload);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Even if an unexpected parsing error occurs, per spec §8 return 200 for idempotency or handled callback
            return ResponseEntity.ok().build();
        }
    }
}
