package com.cinemaseat.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentId(String paymentId);
    Optional<Payment> findByBookingRef(String bookingRef);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
