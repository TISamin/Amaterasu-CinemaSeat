package com.cinemaseat.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_ref", nullable = false, unique = true, length = 64)
    private String bookingRef;

    @Column(name = "show_seat_id", nullable = false)
    private Long showSeatId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public String getBookingRef() { return bookingRef; }
    public Long getShowSeatId() { return showSeatId; }
    public String getUserId() { return userId; }
    public BookingStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public void setShowSeatId(Long showSeatId) { this.showSeatId = showSeatId; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setStatus(BookingStatus status) { this.status = status; }
}
