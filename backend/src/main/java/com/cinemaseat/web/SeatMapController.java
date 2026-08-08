package com.cinemaseat.web;

import com.cinemaseat.showseat.SeatMapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shows/{showId}/seats")
public class SeatMapController {

    private final SeatMapService seatMapService;

    public SeatMapController(SeatMapService seatMapService) {
        this.seatMapService = seatMapService;
    }

    /**
     * Lazy expiration: an HELD row whose hold_expires_at is in the past is
     * returned to the client as AVAILABLE. The DB row is not rewritten here —
     * it will be reclaimed on the next hold attempt.
     */
    @GetMapping
    public ResponseEntity<List<SeatDto>> seatMap(@PathVariable Long showId) {
        return seatMapService.seatMap(showId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}