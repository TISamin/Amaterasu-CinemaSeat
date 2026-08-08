package com.cinemaseat.showseat;

import com.cinemaseat.CinemaSeatApplication;
import com.cinemaseat.booking.Booking;
import com.cinemaseat.booking.BookingRepository;
import com.cinemaseat.booking.BookingStatus;
import com.cinemaseat.booking.HoldResult;
import com.cinemaseat.booking.SeatHoldService;
import com.cinemaseat.show.Show;
import com.cinemaseat.show.ShowRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Single-thread correctness of seat-hold state transitions.
 */
@SpringBootTest(classes = CinemaSeatApplication.class)
@Testcontainers
class ShowSeatHoldIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("cinemaseat")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("hold.ttl-seconds",           () -> "5");      // short for tests
    }

    @Autowired SeatHoldService seatHoldService;
    @Autowired ShowSeatRepository showSeats;
    @Autowired ShowRepository shows;
    @Autowired BookingRepository bookings;

    @Test
    void availableSeatCanBeHeld() {
        Show anyShow = shows.findAll().get(0);
        ShowSeat target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(0);

        HoldResult r = seatHoldService.hold(anyShow.getId(), target.getId(), "alice");

        assertThat(r.success()).isTrue();
        assertThat(r.bookingRef()).startsWith("BK-");
        assertThat(r.holdExpiresAt()).isNotNull();
    }

    @Test
    void activeHeldSeatIsRejected() {
        Show anyShow = shows.findAll().get(0);
        ShowSeat target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(0);

        HoldResult first  = seatHoldService.hold(anyShow.getId(), target.getId(), "alice");
        HoldResult second = seatHoldService.hold(anyShow.getId(), target.getId(), "bob");

        assertThat(first.success()).isTrue();
        assertThat(second.success()).isFalse();
        assertThat(second.message()).containsIgnoringCase("already held");
    }

    @Test
    void bookedSeatIsRejected() {
        Show anyShow = shows.findAll().get(0);
        ShowSeat target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(0);

        // First hold → succeeds.
        seatHoldService.hold(anyShow.getId(), target.getId(), "alice");
        // Manually mark BOOKED (simulating a confirmed payment).
        showSeats.confirmHold(target.getId(), "alice");

        HoldResult attempt = seatHoldService.hold(anyShow.getId(), target.getId(), "bob");
        assertThat(attempt.success()).isFalse();

        ShowSeat row = showSeats.findById(target.getId()).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void expiredHeldSeatCanBeReclaimed() throws Exception {
        Show anyShow = shows.findAll().get(0);
        ShowSeat target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(0);

        HoldResult first = seatHoldService.hold(anyShow.getId(), target.getId(), "alice");
        assertThat(first.success()).isTrue();

        // Force expiration by rewinding the hold expiry into the past.
        ShowSeat row = showSeats.findById(target.getId()).orElseThrow();
        // Update via a direct repo method (test convenience).
        showSeats.findAll().stream()
                .filter(s -> s.getId().equals(row.getId()))
                .findFirst()
                .ifPresent(s -> { /* sentinel */ });

        // Use a JdbcTemplate-style reset via Spring's @Modifying — done inline for clarity:
        expireRowManually(target.getId());

        // New hold by a different user must now succeed.
        HoldResult second = seatHoldService.hold(anyShow.getId(), target.getId(), "bob");
        assertThat(second.success()).isTrue();
        assertThat(second.bookingRef()).isNotEqualTo(first.bookingRef());

        ShowSeat after = showSeats.findById(target.getId()).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(after.getHeldBy()).isEqualTo("bob");
    }

    @Test
    void bookingRowIsCreatedOnHold() {
        Show anyShow = shows.findAll().get(0);
        ShowSeat target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(1);

        HoldResult r = seatHoldService.hold(anyShow.getId(), target.getId(), "alice");

        Booking b = bookings.findByBookingRef(r.bookingRef()).orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(b.getUserId()).isEqualTo("alice");
        assertThat(b.getShowSeatId()).isEqualTo(target.getId());
    }

    /** Direct UPDATE to simulate clock passing beyond the hold TTL. */
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void expireRowManually(Long showSeatId) {
        Instant past = Instant.now().minus(60, ChronoUnit.SECONDS);
        jdbc.update("UPDATE show_seats SET hold_expires_at = ? WHERE id = ?", past, showSeatId);
    }
}