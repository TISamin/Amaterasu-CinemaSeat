package com.cinemaseat.payment.gateway;

/**
 * Response shape from the gateway {@code POST /charge}. We only need the
 * {@code payment_id} — the rest is recorded for traceability.
 */
public record GatewayChargeResponse(
        String paymentId,
        String status
) {}