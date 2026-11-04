package com.example.inventoryservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ReservationLineId implements Serializable {
    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "product_id")
    private UUID productId;

    public ReservationLineId() {}

    public ReservationLineId(UUID reservationId, UUID productId) {
        this.reservationId = reservationId;
        this.productId = productId;
    }

    public UUID getReservationId() { return reservationId; }
    public UUID getProductId() { return productId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReservationLineId that)) return false;
        return Objects.equals(reservationId, that.reservationId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reservationId, productId);
    }
}

