package com.cinemaseat.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByPaymentId(String paymentId);

    Optional<Payment> findByBookingRef(String bookingRef);

    /**
     * Conditional UPDATE for the gateway callback flow:
     * PENDING → SUCCEEDED | FAILED | REFUNDED.
     * Returns 1 if a row was updated, 0 if the payment is already in a terminal
     * state (duplicate callback / out-of-order event).
     */
    @Modifying
    @Query(value = """
            UPDATE payments
            SET status = :newStatus,
                updated_at = CURRENT_TIMESTAMP
            WHERE payment_id = :paymentId
              AND status = 'PENDING'
            """, nativeQuery = true)
    int markTerminalIfPending(@Param("paymentId") String paymentId,
                              @Param("newStatus") String newStatus);
}