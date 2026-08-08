package com.cinemaseat.web;

import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.showseat.SeatStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record BookingDto(
        String bookingRef,
        BookingStatus status,
        String paymentStatus,
        Long showId,
        Long showSeatId,
        Long seatId,
        BigDecimal amount,
        Instant holdExpiresAt
) {
    public static BookingDto from(Booking b, Long showId, Long seatId,
                                   BigDecimal amount, Instant holdExpiresAt,
                                   String paymentStatus, SeatStatus seatStatus) {
        return new BookingDto(
                b.getBookingRef(),
                b.getStatus(),
                paymentStatus,
                showId,
                b.getShowSeatId(),
                seatId,
                amount,
                holdExpiresAt
        );
    }
}