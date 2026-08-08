package com.cinemaseat.booking;

import com.cinemaseat.showseat.SeatStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record HoldResult(
        boolean success,
        String bookingRef,
        Long showId,
        Long showSeatId,
        Long seatId,
        SeatStatus status,
        Instant holdExpiresAt,
        BigDecimal amount,
        String message
) {
    public static HoldResult ok(String bookingRef, Long showId, Long showSeatId, Long seatId,
                                 Instant holdExpiresAt, BigDecimal amount) {
        return new HoldResult(true, bookingRef, showId, showSeatId, seatId,
                SeatStatus.HELD, holdExpiresAt, amount, null);
    }

    public static HoldResult conflict(Long showId, Long showSeatId, String message) {
        return new HoldResult(false, null, showId, showSeatId, null, null, null, null, message);
    }
}