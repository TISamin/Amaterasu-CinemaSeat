package com.cinemaseat.gateway;

import com.cinemaseat.payment.PaymentStatus;

import java.math.BigDecimal;

/**
 * Gateway HTTP contract. One method per gateway endpoint documented in
 * docs/API_CONTRACT.md §7–§10 and AGENT_2_CONTEXT.md §7–§8.
 *
 * <p>Implementations MUST throw {@link GatewayException} on non-2xx and
 * {@link GatewayTimeoutException} on request timeout; never crash the JVM.
 */
public interface GatewayClient {

    /**
     * Initiate a charge. Returns the gateway's payment_id and a PENDING status;
     * final state comes via the async callback.
     *
     * @param idempotencyKey stable per (booking_ref, attempt); reused on retry
     * @param mockForce optional {@code X-Mock-Force} value for the provided
     *                  gateway's deterministic test modes
     */
    ChargeResponse charge(ChargeRequest req, String idempotencyKey, String mockForce);

    /**
     * Optional refund endpoint. Documented but not required for the MVP
     * happy-flow (STATE_MACHINE §13: REFUNDED has no booking/seat side-effects).
     */
    RefundResponse refund(String paymentId, BigDecimal amount, String reason, String mockForce);

    OtpSendResponse otpSend(String phone, String ref);

    OtpVerifyResponse otpVerify(String ref, String code);

    record ChargeRequest(BigDecimal amount, String currency, String bookingRef, String callbackUrl) {}

    record ChargeResponse(String paymentId, PaymentStatus status) {}

    record RefundResponse(String refundId, String status) {}

    record OtpSendResponse(boolean ok, String sessionRef, String error) {}

    record OtpVerifyResponse(boolean verified) {}

    /** Non-2xx response or transport error. Never thrown for 4xx callback payloads. */
    class GatewayException extends RuntimeException {
        private final int statusCode;
        private final String body;
        public GatewayException(String message, int statusCode, String body) {
            super(message);
            this.statusCode = statusCode;
            this.body = body;
        }
        public int getStatusCode() { return statusCode; }
        public String getBody() { return body; }
    }

    /** Request exceeded {@code gateway.timeout-ms}. */
    class GatewayTimeoutException extends RuntimeException {
        public GatewayTimeoutException(String message) { super(message); }
        public GatewayTimeoutException(String message, Throwable cause) { super(message, cause); }
    }
}