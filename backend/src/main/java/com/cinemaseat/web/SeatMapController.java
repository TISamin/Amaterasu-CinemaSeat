package com.cinemaseat.web;

import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.show.ShowRepository;
import com.cinemaseat.showseat.SeatStatus;
import com.cinemaseat.showseat.ShowSeat;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shows/{showId}/seats")
public class SeatMapController {

    private final ShowRepository shows;
    private final ShowSeatRepository showSeats;
    private final SeatRepository seats;

    public SeatMapController(ShowRepository shows,
                             ShowSeatRepository showSeats,
                             SeatRepository seats) {
        this.shows = shows;
        this.showSeats = showSeats;
        this.seats = seats;
    }

    /**
     * Lazy expiration: an HELD row whose hold_expires_at is in the past is
     * returned to the client as AVAILABLE. The DB row is not rewritten here —
     * it will be reclaimed on the next hold attempt.
     */
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<SeatDto>> seatMap(@PathVariable Long showId) {
        if (shows.findById(showId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<ShowSeat> rows = showSeats.findByShowIdOrderByIdAsc(showId);
        Map<Long, com.cinemaseat.seat.Seat> seatById = new HashMap<>();
        for (ShowSeat ss : rows) {
            seats.findById(ss.getSeatId()).ifPresent(s -> seatById.put(s.getId(), s));
        }

        Instant now = Instant.now();
        List<SeatDto> result = rows.stream()
                .map(ss -> toDto(ss, seatById.get(ss.getSeatId()), now))
                .toList();
        return ResponseEntity.ok(result);
    }

    private SeatDto toDto(ShowSeat ss, com.cinemaseat.seat.Seat seat, Instant now) {
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