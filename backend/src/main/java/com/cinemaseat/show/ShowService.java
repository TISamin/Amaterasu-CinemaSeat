package com.cinemaseat.show;

import com.cinemaseat.movie.MovieRepository;
import com.cinemaseat.screen.Screen;
import com.cinemaseat.screen.ScreenRepository;
import com.cinemaseat.theatre.Theatre;
import com.cinemaseat.theatre.TheatreRepository;
import com.cinemaseat.web.ShowDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Application service for the shows aggregate.
 *
 * Owns the "upcoming shows for a movie" use case, including the
 * movie-exists check and the screen/theatre joins. The controller
 * never touches the repositories directly.
 */
@Service
public class ShowService {

    private final ShowRepository shows;
    private final MovieRepository movies;
    private final ScreenRepository screens;
    private final TheatreRepository theatres;

    public ShowService(ShowRepository shows,
                       MovieRepository movies,
                       ScreenRepository screens,
                       TheatreRepository theatres) {
        this.shows = shows;
        this.movies = movies;
        this.screens = screens;
        this.theatres = theatres;
    }

    /**
     * @return {@code Optional.empty()} when the movie does not exist;
     *         otherwise the list of shows for that movie, hydrated with
     *         screen + theatre names.
     */
    @Transactional(readOnly = true)
    public Optional<List<ShowDto>> listForMovie(Long movieId) {
        if (movies.findById(movieId).isEmpty()) {
            return Optional.empty();
        }
        List<ShowDto> result = shows.findByMovieIdOrderByStartTimeAsc(movieId).stream()
                .map(this::toDto)
                .toList();
        return Optional.of(result);
    }

    private ShowDto toDto(Show s) {
        Screen screen = screens.findById(s.getScreenId()).orElse(null);
        Theatre theatre = screen == null ? null : theatres.findById(screen.getTheatreId()).orElse(null);
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