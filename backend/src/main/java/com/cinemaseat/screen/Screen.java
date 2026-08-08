package com.cinemaseat.screen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "screens")
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "theatre_id", nullable = false)
    private Long theatreId;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    public Long getId() { return id; }
    public Long getTheatreId() { return theatreId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
