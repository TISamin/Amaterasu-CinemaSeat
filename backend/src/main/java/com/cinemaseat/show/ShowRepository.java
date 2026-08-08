package com.cinemaseat.show;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {
    List<Show> findByMovieIdOrderByStartTimeAsc(Long movieId);
}