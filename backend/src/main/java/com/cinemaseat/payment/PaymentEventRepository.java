package com.cinemaseat.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    boolean existsByEventId(String eventId);

    /**
     * Atomic insert-on-conflict. Returns 1 if the row was inserted (first
     * delivery for this {@code event_id}) or 0 if it was a duplicate
     * (UNIQUE(event_id) violated). Never throws on duplicate — callers
     * check the return value to decide whether to transition state.
     */
    @Query(value = """
            INSERT INTO payment_events
                (event_id, payment_id, booking_ref, status, amount, currency)
            VALUES
                (:eventId, :paymentId, :bookingRef, :status, :amount, :currency)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("paymentId") String paymentId,
                       @Param("bookingRef") String bookingRef,
                       @Param("status") String status,
                       @Param("amount") BigDecimal amount,
                       @Param("currency") String currency);
}
