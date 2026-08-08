package com.cinemaseat.payment.gateway;

/**
 * Thrown when the gateway is unreachable or returns a non-2xx response.
 * Caller (PaymentService) decides whether to fail the booking.
 */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}