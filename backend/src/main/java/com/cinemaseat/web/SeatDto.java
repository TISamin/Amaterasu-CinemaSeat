package com.cinemaseat.web;

import com.cinemaseat.showseat.SeatStatus;

import java.math.BigDecimal;

public record SeatDto(
        Long id,
        Long seatId,
        String row,
        Integer number,
        BigDecimal price,
        SeatStatus status
) {}