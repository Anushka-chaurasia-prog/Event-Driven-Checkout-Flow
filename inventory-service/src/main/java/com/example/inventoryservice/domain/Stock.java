package com.example.inventoryservice.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Stock levels for a product.
 */
@Entity
@Table(name = "stock")
public class Stock {
    @Id
    @Column(name = "product_id")
    private UUID productId; // primary key, FK to Product

    @Column(name = "available", nullable = false)
    private int available;

    @Column(name = "reserved", nullable = false)
    private int reserved;

    @Column(name = "sold", nullable = false)
    private int sold = 0;

    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt = java.time.Instant.now();

    protected Stock() {}

    public Stock(UUID productId, int available) {
        this.productId = productId;
        this.available = available;
        this.reserved = 0;
    }

    public UUID getProductId() { return productId; }
    public int getAvailable() { return available; }
    public int getReserved() { return reserved; }
    public int getSold() { return sold; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }

    public void setAvailable(int available) { this.available = available; }
    public void setReserved(int reserved) { this.reserved = reserved; }
    public void setSold(int sold) { this.sold = sold; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}

