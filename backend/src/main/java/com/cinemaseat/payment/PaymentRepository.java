package com.cinemaseat.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    Optional<Payment> findByPaymentId(String paymentId);

    List<Payment> findByBookingRefOrderByCreatedAtDesc(String bookingRef);

    /** Most recent payment row for a booking, if any. */
    default Optional<Payment> findLatestByBookingRef(String bookingRef) {
        return findByBookingRefOrderByCreatedAtDesc(bookingRef).stream().findFirst();
    }

    /**
     * Atomic conditional UPDATE — used by the callback handler. Transitions
     * PENDING → {@code target} only if the row is still PENDING (duplicate or
     * out-of-order callbacks become no-ops). Returns 1 on transition, 0 if
     * the row was missing or already in a terminal state.
     */
    @Modifying
    @Query(value = """
            UPDATE payments
            SET status = :target,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND status = 'PENDING'
            """, nativeQuery = true)
    int markStatus(@Param("id") Long id, @Param("target") String target);

    /**
     * Persist the gateway's payment_id after the first successful /charge.
     * Returns 1 on update, 0 if the row vanished (shouldn't happen — kept for
     * testability and audit).
     */
    @Modifying
    @Query(value = """
            UPDATE payments
            SET payment_id = :paymentId,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id
              AND payment_id IS NULL
            """, nativeQuery = true)
    int assignGatewayPaymentId(@Param("id") Long id, @Param("paymentId") String paymentId);

    /**
     * Insert a fresh PENDING payment row using ON CONFLICT on idempotency_key.
     * Returns the inserted row's id (the generated BIGSERIAL). If the key
     * already exists, returns the existing row's id — never throws.
     */
    @Query(value = """
            WITH ins AS (
                INSERT INTO payments
                    (payment_id, booking_ref, status, amount, currency, idempotency_key)
                VALUES
                    (NULL, :bookingRef, 'PENDING', :amount, :currency, :idempotencyKey)
                ON CONFLICT (idempotency_key) DO NOTHING
                RETURNING id
            )
            SELECT id FROM ins
            UNION ALL
            SELECT id FROM payments WHERE idempotency_key = :idempotencyKey
            LIMIT 1
            """, nativeQuery = true)
    Long upsertPending(@Param("bookingRef") String bookingRef,
                       @Param("amount") BigDecimal amount,
                       @Param("currency") String currency,
                       @Param("idempotencyKey") String idempotencyKey);
}
