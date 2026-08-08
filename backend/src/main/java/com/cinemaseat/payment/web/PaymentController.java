package com.cinemaseat.payment.web;

import com.cinemaseat.payment.Payment;
import com.cinemaseat.payment.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/bookings/{bookingRef}/pay}.
 *
 * Always returns {@code 202 Accepted} on a successful gateway handshake.
 * The final booking state arrives via the asynchronous gateway callback —
 * see API_CONTRACT §7 and §13 (invariant #7).
 */
@RestController
@RequestMapping("/api/bookings")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/{bookingRef}/pay")
    public ResponseEntity<PayResponse> pay(@PathVariable String bookingRef) {
        Payment p = payments.pay(bookingRef);
        PayResponse body = new PayResponse(
                p.getBookingRef(),
                p.getPaymentId(),
                p.getStatus(),
                p.getAmount(),
                p.getCurrency(),
                null // hold_expires_at is owned by Agent 1; client can fetch from /api/bookings/{ref}
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}