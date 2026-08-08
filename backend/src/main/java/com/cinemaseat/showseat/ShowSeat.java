package com.cinemaseat.showseat;

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
 * One row per physical seat for one specific show.
 * Concurrency is enforced at the SQL level (atomic UPDATE) — see
 * ShowSeatRepository#tryHold. The @Version field is intentionally NOT used so
 * the application never silently pessimizes the atomic UPDATE.
 */
@Entity
@Table(name = "show_seats")
public class ShowSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "show_id", nullable = false)
    private Long showId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status;

    @Column(name = "held_by")
    private String heldBy;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;

    public Long getId() { return id; }
    public Long getShowId() { return showId; }
    public Long getSeatId() { return seatId; }
    public SeatStatus getStatus() { return status; }
    public String getHeldBy() { return heldBy; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public BigDecimal getPrice() { return price; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Test-only convenience constructor for building stub rows. */
    public static ShowSeat forTest(Long id, BigDecimal price) {
        ShowSeat s = new ShowSeat();
        s.id = id;
        s.price = price;
        s.status = SeatStatus.AVAILABLE;
        return s;
    }
}
