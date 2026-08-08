package com.cinemaseat.payment.web;

import com.cinemaseat.payment.PaymentStatus;
import com.cinemaseat.payment.PaymentService;
import com.cinemaseat.payment.PaymentService.GatewayCallbackPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Receives asynchronous gateway callbacks at {@code POST /api/payments/callback}.
 *
 * The gateway interprets non-2xx as delivery failure. To prevent infinite retry
 * loops we MUST return {@code 200 OK} for:
 * <ul>
 *   <li>first-time valid callbacks</li>
 *   <li>duplicate callbacks (same {@code event_id})</li>
 *   <li>unknown bookings (orphan callbacks — never 404 or 409)</li>
 * </ul>
 *
 * Idempotency is guaranteed by {@code UNIQUE(event_id)} on {@code payment_events}.
 * See API_CONTRACT §8 and §13 (invariants #5, #6).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackController.class);

    private final PaymentService payments;

    public PaymentCallbackController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping("/callback")
    public ResponseEntity<Void> callback(@RequestBody Map<String, Object> body) {
        GatewayCallbackPayload payload = parse(body);
        try {
            payments.handleCallback(payload);
        } catch (RuntimeException ex) {
            // Never bubble a non-2xx back to the gateway for a recognised event.
            // UNIQUE(event_id) guarantees we won't double-apply, so swallowing is
            // safer than risking a poison-message loop.
            log.warn("CALLBACK_HANDLER_ERROR eventId={} bookingRef={} cause={}",
                    payload.eventId(), payload.bookingRef(), ex.toString());
        }
        return ResponseEntity.ok().build();
    }

    @SuppressWarnings("unchecked")
    private static GatewayCallbackPayload parse(Map<String, Object> body) {
        if (body == null) {
            return new GatewayCallbackPayload(null, null, null, PaymentStatus.FAILED,
                    null, null, null);
        }
        String eventId    = str(body.get("event_id"));
        String paymentId  = str(body.get("payment_id"));
        String bookingRef = str(body.get("booking_ref"));
        PaymentStatus status = parseStatus(body.get("status"));
        BigDecimal amount   = toBigDecimal(body.get("amount"));
        String currency     = str(body.get("currency"));
        Instant timestamp   = toInstant(body.get("timestamp"));
        return new GatewayCallbackPayload(eventId, paymentId, bookingRef, status,
                amount, currency, timestamp);
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    private static PaymentStatus parseStatus(Object o) {
        if (o == null) return PaymentStatus.FAILED;
        try { return PaymentStatus.valueOf(o.toString().toUpperCase()); }
        catch (IllegalArgumentException e) { return PaymentStatus.FAILED; }
    }

    private static BigDecimal toBigDecimal(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(o.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        try { return Instant.parse(o.toString()); }
        catch (Exception e) { return null; }
    }
}