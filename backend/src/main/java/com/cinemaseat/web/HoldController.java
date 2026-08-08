package com.cinemaseat.web;

import com.cinemaseat.booking.HoldResult;
import com.cinemaseat.booking.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/seats/{showSeatId}/hold")
public class HoldController {

    private final SeatHoldService holdService;

    public HoldController(SeatHoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping
    public ResponseEntity<?> hold(@PathVariable Long showId,
                                  @PathVariable Long showSeatId,
                                  @Valid @RequestBody HoldRequest req) {
        HoldResult result = holdService.hold(showId, showSeatId, req.userId());
        if (result.success()) {
            return ResponseEntity.ok(HoldResponse.from(result));
        }
        return ResponseEntity.status(409)
                .body(new ErrorResponse("SEAT_UNAVAILABLE", result.message()));
    }
}