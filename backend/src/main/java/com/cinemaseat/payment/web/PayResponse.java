package com.cinemaseat.payment.web;

import com.cinemaseat.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response shape for {@code POST /api/bookings/{bookingRef}/pay}.
 * Always 202 Accepted — the gateway decides final state.
 */
public record PayResponse(
        String bookingRef,
        String paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        Instant holdExpiresAt
) {}