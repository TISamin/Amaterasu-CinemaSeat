package com.cinemaseat.web;

import com.cinemaseat.movie.Movie;

public record MovieDto(
        Long id,
        String title,
        String description,
        Integer durationMinutes,
        String posterUrl
) {
    public static MovieDto from(Movie m) {
        return new MovieDto(m.getId(), m.getTitle(), m.getDescription(),
                m.getDurationMinutes(), m.getPosterUrl());
    }
}