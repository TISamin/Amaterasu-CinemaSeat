package com.cinemaseat.web;

import com.cinemaseat.booking.HoldResult;

import java.math.BigDecimal;
import java.time.Instant;

public record HoldResponse(
        String bookingRef,
        Long showId,
        Long seatId,
        String status,
        Instant holdExpiresAt,
        BigDecimal amount
) {
    public static HoldResponse from(HoldResult r) {
        return new HoldResponse(
                r.bookingRef(),
                r.showId(),
                r.showSeatId(),
                r.status() == null ? null : r.status().name(),
                r.holdExpiresAt(),
                r.amount()
        );
    }
}