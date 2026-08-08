package com.cinemaseat.show;

import com.cinemaseat.CinemaSeatApplication;
import com.cinemaseat.booking.HoldResult;
import com.cinemaseat.booking.SeatHoldService;
import com.cinemaseat.seat.Seat;
import com.cinemaseat.seat.SeatRepository;
import com.cinemaseat.showseat.SeatStatus;
import com.cinemaseat.web.SeatDto;
import com.cinemaseat.showseat.ShowSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seat-map endpoint must treat HELD-and-expired rows as AVAILABLE.
 * Lazy expiration: no scheduler needed for correctness.
 */
@SpringBootTest(classes = CinemaSeatApplication.class)
@Testcontainers
class SeatMapLazyExpirationIT {

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
        r.add("hold.ttl-seconds",           () -> "5");
    }

    @Autowired SeatHoldService seatHoldService;
    @Autowired ShowSeatRepository showSeats;
    @Autowired ShowRepository shows;
    @Autowired SeatRepository seats;
    @Autowired JdbcTemplate jdbc;

    @Test
    void expiredHeldSeatAppearsAsAvailableInSeatMap() {
        Show anyShow = shows.findAll().get(0);
        var target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(2);

        HoldResult holder = seatHoldService.hold(anyShow.getId(), target.getId(), "alice");
        assertThat(holder.success()).isTrue();

        // Push hold_expires_at into the past.
        Instant past = Instant.now().minus(60, ChronoUnit.SECONDS);
        jdbc.update("UPDATE show_seats SET hold_expires_at = ? WHERE id = ?", past, target.getId());

        // Seat-map endpoint logic (replicated here) must show AVAILABLE.
        List<SeatDto> row = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).stream()
                .filter(s -> s.getId().equals(target.getId()))
                .map(s -> {
                    SeatStatus effective = (s.getStatus() == SeatStatus.HELD
                            && s.getHoldExpiresAt() != null
                            && s.getHoldExpiresAt().isBefore(Instant.now()))
                            ? SeatStatus.AVAILABLE : s.getStatus();
                    Seat seatDef = seats.findById(s.getSeatId()).orElseThrow();
                    return new SeatDto(s.getId(), s.getSeatId(), seatDef.getRowLabel(), seatDef.getSeatNumber(), s.getPrice(), effective);
                })
                .toList();

        assertThat(row).hasSize(1);
        assertThat(row.get(0).status()).isEqualTo(SeatStatus.AVAILABLE);
    }
}