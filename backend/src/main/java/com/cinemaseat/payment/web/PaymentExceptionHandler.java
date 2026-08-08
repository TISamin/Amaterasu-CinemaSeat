package com.cinemaseat.payment.web;

import com.cinemaseat.payment.gateway.GatewayException;
import com.cinemaseat.web.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps payment-layer exceptions to {@link ErrorResponse} envelopes
 * (API_CONTRACT §12).
 *
 * Scoped to {@code com.cinemaseat.payment.web} so it doesn't shadow
 * Agent 1's existing handlers — extend it as more endpoints are added.
 */
@RestControllerAdvice(basePackages = "com.cinemaseat.payment.web")
public class PaymentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("BAD_REQUEST", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_STATE", ex.getMessage()));
    }

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ErrorResponse> handleGateway(GatewayException ex) {
        log.warn("GATEWAY_ERROR {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ErrorResponse("GATEWAY_UNAVAILABLE",
                        "Payment gateway is currently unavailable. Please retry."));
    }
}