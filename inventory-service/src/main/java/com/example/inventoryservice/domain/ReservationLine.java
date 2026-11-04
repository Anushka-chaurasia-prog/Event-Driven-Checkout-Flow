package com.example.inventoryservice.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * A line in a reservation – one product and quantity.
 */
@Entity
@Table(name = "reservation_lines")
public class ReservationLine {
    @EmbeddedId
    private ReservationLineId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("reservationId")
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    protected ReservationLine() {}

    public ReservationLine(UUID reservationId, UUID productId, int quantity, long unitPriceCents) {
        this.id = new ReservationLineId(reservationId, productId);
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    @Column(name = "quantity", nullable = false)
    private int quantity;

    public ReservationLineId getId() { return id; }
    public UUID getReservationId() { return id.getReservationId(); }
    public UUID getProductId() { return id.getProductId(); }
    public int getQuantity() { return quantity; }
    public long getUnitPriceCents() { return unitPriceCents; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }
}

