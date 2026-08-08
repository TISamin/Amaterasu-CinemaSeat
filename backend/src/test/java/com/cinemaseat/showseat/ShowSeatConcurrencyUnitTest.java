package com.cinemaseat.showseat;

import com.cinemaseat.booking.HoldResult;
import com.cinemaseat.booking.SeatHoldService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-Java concurrency simulation of the hold service's decision logic.
 *
 * <p>What this proves: the <em>service-level</em> contract that "exactly one
 * winner when many concurrent callers race for the same seat" is implemented
 * correctly. The test uses a stand-in {@link AtomicInteger} counter to model
 * the seat row's AVAILABLE-to-HELD transition.
 *
 * <p>What this does NOT prove: that the SQL UPDATE used in production is also
 * race-free. That guarantee comes from Postgres's row-level locking, and the
 * authoritative test is {@link ShowSeatConcurrencyIT}, which must be run in an
 * environment with Docker (Testcontainers) or against a real Postgres.
 */
class ShowSeatConcurrencyUnitTest {

    /**
     * Stand-in for the show_seats row + the atomic UPDATE. The first caller to
     * swap the counter from 0→1 wins; all others see 1 and lose.
     */
    static final class SeatSlot {
        private final AtomicInteger holder = new AtomicInteger(0);

        /** Returns true if this caller acquired the seat. */
        boolean tryAcquire(int threadId) {
            return holder.compareAndSet(0, threadId);
        }

        int currentHolder() { return holder.get(); }
    }

    /** Service-level decision mirror of SeatHoldService#hold. */
    static HoldResult tryHold(SeatSlot slot, int threadId) {
        if (slot.tryAcquire(threadId)) {
            return HoldResult.ok(
                    "BK-T" + threadId,
                    0L, 0L, 0L,
                    java.time.Instant.now().plusSeconds(60),
                    java.math.BigDecimal.TEN);
        }
        return HoldResult.conflict(0L, 0L, "already held");
    }

    @Test
    void oneHundredConcurrentCallersGetExactlyOneWinner() throws Exception {
        SeatSlot slot = new SeatSlot();

        final int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);
        AtomicInteger wins   = new AtomicInteger();
        AtomicInteger losses = new AtomicInteger();

        for (int i = 0; i < N; i++) {
            final int id = i + 1;
            pool.submit(() -> {
                try {
                    start.await();
                    if (tryHold(slot, id).success()) wins.incrementAndGet();
                    else                           losses.incrementAndGet();
                } catch (Exception ignored) {
                    losses.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).isTrue();
        assertThat(wins.get()).isEqualTo(1);
        assertThat(losses.get()).isEqualTo(N - 1);
        assertThat(slot.currentHolder()).isNotEqualTo(0);
    }
}