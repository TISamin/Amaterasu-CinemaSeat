package com.cinemaseat.web;

import com.cinemaseat.movie.MovieRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieRepository movies;

    public MovieController(MovieRepository movies) {
        this.movies = movies;
    }

    @GetMapping
    public List<MovieDto> listMovies() {
        return movies.findAll().stream().map(MovieDto::from).toList();
    }
}