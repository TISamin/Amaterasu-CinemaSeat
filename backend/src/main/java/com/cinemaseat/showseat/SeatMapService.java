package com.cinemaseat.showseat;

import com.cinemaseat.seat.Seat;
import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.show.ShowRepository;
import com.cinemaseat.web.SeatDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Application service for the seat-map use case.
 *
 * Lazy expiration lives here: an {@code HELD} row whose {@code hold_expires_at}
 * is in the past is reported as {@code AVAILABLE} to the caller, but the DB
 * row is not rewritten — it will be reclaimed on the next hold attempt
 * (see {@link ShowSeatRepository#tryHold}).
 */
@Service
public class SeatMapService {

    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final SeatRepository seats;

    public SeatMapService(ShowRepository shows,
                         ShowSeatRepository showSeats,
                         SeatRepository seats) {
        this.shows = shows;
        this.showSeats = showSeats;
        this.seats = seats;
    }

    /**
     * @return {@code Optional.empty()} when the show does not exist;
     *         otherwise the list of seat-map entries (one per {@code show_seat}).
     */
    @Transactional(readOnly = true)
    public Optional<List<SeatDto>> seatMap(Long showId) {
        if (shows.findById(showId).isEmpty()) {
            return Optional.empty();
        }

        List<ShowSeat> rows = showSeats.findByShowIdOrderByIdAsc(showId);
        Map<Long, Seat> seatById = new HashMap<>();
        for (ShowSeat ss : rows) {
            seats.findById(ss.getSeatId()).ifPresent(s -> seatById.put(s.getId(), s));
        }

        Instant now = Instant.now();
        List<SeatDto> result = rows.stream()
                .map(ss -> toDto(ss, seatById.get(ss.getSeatId()), now))
                .toList();
        return Optional.of(result);
    }

    private SeatDto toDto(ShowSeat ss, Seat seat, Instant now) {
        SeatStatus effective = ss.getStatus();
        if (effective == SeatStatus.HELD
                && ss.getHoldExpiresAt() != null
                && ss.getHoldExpiresAt().isBefore(now)) {
            effective = SeatStatus.AVAILABLE;
        }
        String row = seat == null ? null : seat.getRowLabel();
        Integer num = seat == null ? null : seat.getSeatNumber();
        return new SeatDto(ss.getId(), ss.getSeatId(), row, num, ss.getPrice(), effective);
    }
}