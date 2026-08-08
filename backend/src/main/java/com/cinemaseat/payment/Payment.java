package com.cinemaseat.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment attempt for a booking. Maps to {@code payments} table (V3 migration).
 *
 * <p>Idempotency on the outbound {@code /charge} call is enforced by the
 * {@code uq_payments_idempotency_key} UNIQUE constraint. Idempotency on the
 * inbound gateway callback is enforced separately by {@code payment_events}.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Gateway-side id; assigned asynchronously after the first /charge round-trip. */
    @Column(name = "payment_id", unique = true, length = 128)
    private String paymentId;

    @Column(name = "booking_ref", nullable = false, length = 64)
    private String bookingRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public String getBookingRef() { return bookingRef; }
    public PaymentStatus getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    /** Test-only convenience factory for stub payment rows. */
    public static Payment forTest(Long id, String paymentId, String bookingRef,
                                  PaymentStatus status, BigDecimal amount,
                                  String currency, String idempotencyKey) {
        Payment p = new Payment();
        p.id = id;
        p.paymentId = paymentId;
        p.bookingRef = bookingRef;
        p.status = status;
        p.amount = amount;
        p.currency = currency;
        p.idempotencyKey = idempotencyKey;
        return p;
    }
}
