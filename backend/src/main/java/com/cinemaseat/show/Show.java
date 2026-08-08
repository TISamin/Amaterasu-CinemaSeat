package com.cinemaseat.show;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "movie_id", nullable = false)
    private Long movieId;

    @Column(name = "screen_id", nullable = false)
    private Long screenId;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Long getMovieId() { return movieId; }
    public Long getScreenId() { return screenId; }
    public Instant getStartTime() { return startTime; }
    public BigDecimal getBasePrice() { return basePrice; }
    public Instant getCreatedAt() { return createdAt; }
}
