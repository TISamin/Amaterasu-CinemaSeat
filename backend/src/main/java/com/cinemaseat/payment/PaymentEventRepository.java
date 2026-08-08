package com.cinemaseat.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, Long> {

    /**
     * Used to short-circuit duplicate callback handling before attempting INSERT.
     * The INSERT itself is the authoritative idempotency primitive (UNIQUE event_id).
     */
    Optional<PaymentEvent> findByEventId(String eventId);
}