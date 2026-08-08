package com.cinemaseat.movie;

import com.cinemaseat.web.MovieDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service for the movies aggregate.
 *
 * Sits between {@link MovieController} and {@link MovieRepository},
 * so the controller stays free of persistence concerns and the repository
 * stays free of transport/DTO concerns.
 */
@Service
public class MovieService {

    private final MovieRepository movies;

    public MovieService(MovieRepository movies) {
        this.movies = movies;
    }

    /** All movies, ordered by id (callers can re-sort on the client). */
    @Transactional(readOnly = true)
    public List<MovieDto> listAll() {
        return movies.findAll().stream().map(MovieDto::from).toList();
    }
}
