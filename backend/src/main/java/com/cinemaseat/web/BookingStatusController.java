package com.cinemaseat.web;

import com.cinemaseat.booking.BookingQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingStatusController {

    private final BookingQueryService bookingQueryService;

    public BookingStatusController(BookingQueryService bookingQueryService) {
        this.bookingQueryService = bookingQueryService;
    }

    @GetMapping("/{bookingRef}")
    public ResponseEntity<?> getBooking(@PathVariable String bookingRef) {
        return bookingQueryService.getByRef(bookingRef);
    }
}