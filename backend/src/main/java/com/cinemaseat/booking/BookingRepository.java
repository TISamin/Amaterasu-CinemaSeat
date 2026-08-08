package com.cinemaseat.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingRef(String bookingRef);

    List<Booking> findByShowSeatIdOrderByCreatedAtDesc(Long showSeatId);

    /**
     * Set booking + seat in one call when the payment gateway confirms.
     * Returns 1 if the booking was actually CONFIRMED; 0 if it was already in a terminal state
     * (duplicate callback or already-confirmed by an earlier event).
     */
    @Modifying
    @Query(value = """
            UPDATE bookings
            SET status = 'CONFIRMED',
                updated_at = CURRENT_TIMESTAMP
            WHERE booking_ref = :bookingRef
              AND status = 'PENDING_PAYMENT'
            """, nativeQuery = true)
    int markConfirmed(@Param("bookingRef") String bookingRef);

    @Modifying
    @Query(value = """
            UPDATE bookings
            SET status = 'PAYMENT_FAILED',
                updated_at = CURRENT_TIMESTAMP
            WHERE booking_ref = :bookingRef
              AND status = 'PENDING_PAYMENT'
            """, nativeQuery = true)
    int markPaymentFailed(@Param("bookingRef") String bookingRef);

    @Modifying
    @Query(value = """
            UPDATE bookings
            SET status = 'EXPIRED',
                updated_at = CURRENT_TIMESTAMP
            WHERE booking_ref = :bookingRef
              AND status = 'PENDING_PAYMENT'
            """, nativeQuery = true)
    int markExpired(@Param("bookingRef") String bookingRef);
}