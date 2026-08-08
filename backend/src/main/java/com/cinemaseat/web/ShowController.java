package com.cinemaseat.web;

import com.cinemaseat.movie.MovieRepository;
import com.cinemaseat.screen.ScreenRepository;
import com.cinemaseat.show.Show;
import com.cinemaseat.show.ShowRepository;
import com.cinemaseat.theatre.TheatreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies/{movieId}/shows")
public class ShowController {

    private final ShowRepository shows;
    private final MovieRepository movies;
    private final ScreenRepository screens;
    private final TheatreRepository theatres;

    public ShowController(ShowRepository shows,
                          MovieRepository movies,
                          ScreenRepository screens,
                          TheatreRepository theatres) {
        this.shows = shows;
        this.movies = movies;
        this.screens = screens;
        this.theatres = theatres;
    }

    @GetMapping
    public ResponseEntity<List<ShowDto>> listShowsForMovie(@PathVariable Long movieId) {
        if (movies.findById(movieId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<ShowDto> result = shows.findByMovieIdOrderByStartTimeAsc(movieId).stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    private ShowDto toDto(Show s) {
        var screen = screens.findById(s.getScreenId()).orElse(null);
        var theatre = screen == null ? null : theatres.findById(screen.getTheatreId()).orElse(null);
        return new ShowDto(
                s.getId(),
                s.getMovieId(),
                theatre == null ? null : theatre.getId(),
                theatre == null ? null : theatre.getName(),
                screen == null ? null : screen.getId(),
                screen == null ? null : screen.getName(),
                s.getStartTime(),
                s.getBasePrice()
        );
    }
}