package com.cinemaseat.payment.gateway;

import java.math.BigDecimal;

/**
 * Body sent to the gateway {@code POST /charge} endpoint. The provided mock
 * gateway accepts these fields and echoes {@code payment_id} in its response.
 */
public record GatewayChargeRequest(
        String bookingRef,
        BigDecimal amount,
        String currency,
        String callbackUrl
) {}