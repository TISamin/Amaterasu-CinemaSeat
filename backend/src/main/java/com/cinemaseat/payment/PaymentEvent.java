package com.cinemaseat.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per gateway callback delivery. The {@code uq_payment_events_event_id}
 * UNIQUE constraint is the duplicate-callback dedup primitive
 * (DATABASE_CONTRACT §15–§16, STATE_MACHINE §14).
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(name = "payment_id", length = 128)
    private String paymentId;

    @Column(name = "booking_ref", length = 64)
    private String bookingRef;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 8)
    private String currency;

    @Column(name = "received_at", nullable = false, insertable = false, updatable = false)
    private Instant receivedAt;

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getPaymentId() { return paymentId; }
    public String getBookingRef() { return bookingRef; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getReceivedAt() { return receivedAt; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setStatus(String status) { this.status = status; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
}
