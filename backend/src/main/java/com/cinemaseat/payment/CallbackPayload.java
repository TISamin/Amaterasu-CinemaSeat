package com.cinemaseat.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Gateway callback payload. Field names match docs/API_CONTRACT.md §8
 * (snake_case). Only the SUCCEEDED / FAILED / REFUNDED states are valid
 * callbacks (PENDING is never delivered as a callback).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CallbackPayload(
        String event_id,
        String payment_id,
        String booking_ref,
        String status,
        BigDecimal amount,
        String currency,
        String timestamp
) {
    public boolean statusIsOneOf(String... allowed) {
        if (status == null) return false;
        for (String a : allowed) if (a.equals(status)) return true;
        return false;
    }
}