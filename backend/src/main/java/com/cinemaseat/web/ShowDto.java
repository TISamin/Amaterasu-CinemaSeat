package com.cinemaseat.web;

import com.cinemaseat.show.Show;

import java.math.BigDecimal;
import java.time.Instant;

public record ShowDto(
        Long id,
        Long movieId,
        Long theatreId,
        String theatreName,
        Long screenId,
        String screenName,
        Instant startTime,
        BigDecimal price
) {}