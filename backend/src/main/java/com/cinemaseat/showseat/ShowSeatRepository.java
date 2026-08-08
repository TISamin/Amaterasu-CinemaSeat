package com.cinemaseat.showseat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ShowSeatRepository extends JpaRepository<ShowSeat, Long> {

    /**
     * The ONLY correctness primitive for seat acquisition.
     *
     * Encodes DATABASE_CONTRACT §9. Exactly one row update ⇒ success.
     * Zero rows updated ⇒ seat unavailable (held by someone else, booked, or absent).
     */
    @Modifying
    @Query(value = """
            UPDATE show_seats
            SET status = 'HELD',
                held_by = :userId,
                hold_expires_at = :expiresAt,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :showSeatId
              AND show_id = :showId
              AND (
                    status = 'AVAILABLE'
                 OR (status = 'HELD' AND hold_expires_at < CURRENT_TIMESTAMP)
              )
            """, nativeQuery = true)
    int tryHold(@Param("showId") Long showId,
                @Param("showSeatId") Long showSeatId,
                @Param("userId") String userId,
                @Param("expiresAt") Instant expiresAt);

    /** Used by the seat-map endpoint. Expired HELD rows are returned for the mapper to relabel. */
    List<ShowSeat> findByShowIdOrderByIdAsc(Long showId);

    Optional<ShowSeat> findByIdAndShowId(Long id, Long showId);

    /**
     * Bump HELD → BOOKED for the seat, only if currently HELD by the booking's user
     * and not expired. Used by Agent 2's confirmBooking flow.
     */
    @Modifying
    @Query(value = """
            UPDATE show_seats
            SET status = 'BOOKED',
                hold_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :showSeatId
              AND status = 'HELD'
              AND held_by = :userId
              AND hold_expires_at > CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int confirmHold(@Param("showSeatId") Long showSeatId,
                    @Param("userId") String userId);

    /**
     * Release a held seat (HELD → AVAILABLE) for the given user.
     * Used by Agent 2 for payment-failure and hold-expiration flows.
     */
    @Modifying
    @Query(value = """
            UPDATE show_seats
            SET status = 'AVAILABLE',
                held_by = NULL,
                hold_expires_at = NULL,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :showSeatId
              AND status = 'HELD'
              AND held_by = :userId
            """, nativeQuery = true)
    int releaseHold(@Param("showSeatId") Long showSeatId,
                    @Param("userId") String userId);
}