package com.cinemaseat.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    @Column(name = "booking_ref", nullable = false, length = 32)
    private String bookingRef;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    @PrePersist
    protected void onCreate() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
        if (currency == null) {
            currency = "BDT";
        }
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getPaymentId() { return paymentId; }
    public String getBookingRef() { return bookingRef; }
    public String getStatus() { return status; }
    public Integer getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public Instant getReceivedAt() { return receivedAt; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setStatus(String status) { this.status = status; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
}
