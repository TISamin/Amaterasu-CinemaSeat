package com.cinemaseat.showseat;

import com.cinemaseat.CinemaSeatApplication;
import com.cinemaseat.booking.HoldResult;
import com.cinemaseat.booking.SeatHoldService;
import com.cinemaseat.config.HoldProperties;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headline test for Agent 1: 100 concurrent hold attempts on the same
 * ShowSeat. Exactly one must succeed; 99 must be rejected; oversell must be 0.
 */
@SpringBootTest(classes = CinemaSeatApplication.class)
@Testcontainers
class ShowSeatConcurrencyIT {

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
        r.add("hold.ttl-seconds",           () -> "60");
    }

    @Autowired SeatHoldService seatHoldService;
    @Autowired ShowSeatRepository showSeats;
    @Autowired ShowRepository shows;
    @Autowired HoldProperties holdProps;

    @Test
    void oneHundredConcurrentHoldersProduceExactlyOneWinner() throws Exception {
        Show anyShow = shows.findAll().get(0);
        ShowSeat target = showSeats.findByShowIdOrderByIdAsc(anyShow.getId()).get(0);
        Long showId = anyShow.getId();
        Long showSeatId = target.getId();

        final int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<HoldResult>> futures = new ArrayList<>(N);
        for (int i = 0; i < N; i++) {
            final String user = "user-" + i;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    HoldResult r = seatHoldService.hold(showId, showSeatId, user);
                    if (r.success()) successes.incrementAndGet();
                    else              conflicts.incrementAndGet();
                    return r;
                } catch (Exception e) {
                    conflicts.incrementAndGet();
                    return HoldResult.conflict(showId, showSeatId, e.getMessage());
                } finally {
                    done.countDown();
                }
            }));
        }

        start.countDown();                        // fire all threads simultaneously
        boolean finished = done.await(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("all threads finished").isTrue();
        assertThat(successes.get()).as("exactly one successful hold").isEqualTo(1);
        assertThat(conflicts.get()).as("99 conflicts").isEqualTo(99);
        assertThat(successes.get() + conflicts.get()).isEqualTo(N);

        // oversell check — the seat row reflects exactly one HELD
        ShowSeat after = showSeats.findById(showSeatId).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(after.getHeldBy()).isNotNull();
    }
}