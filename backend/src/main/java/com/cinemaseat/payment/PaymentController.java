package com.cinemaseat.payment;

import com.cinemaseat.gateway.GatewayClient;
import com.cinemaseat.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

/**
 * Payment + OTP HTTP surface.
 *
 * <ul>
 *   <li>POST /api/bookings/{bookingRef}/pay — start a payment attempt. 202 with
 *       a pending payment row. Idempotent on retry; same idempotency_key
 *       reuses the existing PENDING row.</li>
 *   <li>POST /api/payments/callback — gateway delivers a terminal status. Always
 *       200; dedup is by event_id.</li>
 *   <li>POST /api/otp/send — proxy to the gateway's OTP send endpoint. Failures
 *       map to a 502 with a friendly body; never crash.</li>
 *   <li>POST /api/otp/verify — proxy to the gateway's OTP verify endpoint.</li>
 * </ul>
 *
 * <p>Per docs/API_CONTRACT.md, the callback may arrive before /pay returns.
 * Per STATE_MACHINE.md §15, the gateway HTTP call is NEVER inside an open DB
 * transaction — that's enforced in {@link PaymentService}.
 */
@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    // ---- /pay -------------------------------------------------------------

    @PostMapping("/api/bookings/{bookingRef}/pay")
    public ResponseEntity<Map<String, Object>> pay(
            @PathVariable String bookingRef,
            @RequestHeader(value = "X-Mock-Force", required = false) String mockForce) {
        PaymentService.PayResult res = (mockForce == null || mockForce.isBlank())
                ? payments.pay(bookingRef)
                : payments.pay(bookingRef, mockForce);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(resBody(res));
    }

    private static Map<String, Object> resBody(PaymentService.PayResult res) {
        PaymentStatus s = res.status();
        return Map.of(
                "bookingRef", res.bookingRef(),
                "paymentId", res.paymentId(),
                "paymentStatus", s.name(),
                "reused", res.reused()
        );
    }

    // ---- /payments/callback ----------------------------------------------

    @PostMapping("/api/payments/callback")
    public ResponseEntity<Map<String, Object>> callback(@RequestBody CallbackPayload payload) {
        PaymentService.CallbackOutcome out = payments.handleCallback(payload);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "duplicate", out.duplicate(),
                "amountMismatch", out.amountMismatch(),
                "bookingRef", out.bookingRef(),
                "paymentId", out.paymentId(),
                "paymentStatus", Objects.toString(out.finalPaymentStatus(), "")));
    }

    // ---- /otp/send --------------------------------------------------------

    @PostMapping("/api/otp/send")
    public ResponseEntity<GatewayClient.OtpSendResponse> otpSend(@RequestBody Map<String, String> body) {
        String phone = Objects.requireNonNull(body.get("phone"), "phone");
        String ref = Objects.requireNonNull(body.get("ref"), "ref");
        GatewayClient.OtpSendResponse r = payments.forwardOtpSend(phone, ref);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/api/otp/verify")
    public ResponseEntity<GatewayClient.OtpVerifyResponse> otpVerify(@RequestBody Map<String, String> body) {
        String ref = Objects.requireNonNull(body.get("ref"), "ref");
        String code = Objects.requireNonNull(body.get("code"), "code");
        GatewayClient.OtpVerifyResponse r = payments.forwardOtpVerify(ref, code);
        return ResponseEntity.ok(r);
    }

    // ===================================================================
    // Error translation. Returning 400/404/409/502 keeps the contract honest.
    // ===================================================================

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> badArg(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("bad_request", e.getMessage()));
    }

    @ExceptionHandler({PaymentNotFoundException.class, PaymentValidationException.class})
    public ResponseEntity<ErrorResponse> unprocessable(RuntimeException e) {
        // 404 for missing booking, 422 for validation. We map validating
        // exceptions to 422 too — anything that says "the request is
        // understandable but can't be applied" is 422 by REST convention.
        HttpStatus s = (e instanceof PaymentNotFoundException)
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(s).body(new ErrorResponse(
                e instanceof PaymentNotFoundException ? "not_found" : "payment_validation",
                e.getMessage()));
    }

    @ExceptionHandler(PaymentTerminalException.class)
    public ResponseEntity<ErrorResponse> terminal(PaymentTerminalException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("payment_terminal", e.getMessage()));
    }

    @ExceptionHandler(GatewayClient.GatewayTimeoutException.class)
    public ResponseEntity<ErrorResponse> gwTimeout(GatewayClient.GatewayTimeoutException e) {
        log.warn("gateway timeout: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ErrorResponse("gateway_timeout", e.getMessage()));
    }

    @ExceptionHandler(GatewayClient.GatewayException.class)
    public ResponseEntity<ErrorResponse> gwError(GatewayClient.GatewayException e) {
        log.warn("gateway error: status={} body={}", e.getStatusCode(), e.getBody());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("gateway_error", e.getMessage()));
    }
}