package com.example.inventoryservice.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reservation representing a lock on stock for a specific order.
 */
@Entity
@Table(name = "reservations")
public class Reservation {
    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationLine> lines = new ArrayList<>();

    protected Reservation() {}

    public Reservation(UUID id, UUID orderId, ReservationStatus status, Instant expiresAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = status;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public List<ReservationLine> getLines() { return lines; }
    public void addLine(ReservationLine line) { lines.add(line); line.setReservation(this); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}

