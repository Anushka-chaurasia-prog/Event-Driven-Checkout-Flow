package com.example.inventoryservice.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Product catalog entry.
 */
@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id;

    @Column(name = "sku", nullable = false, unique = true)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "INR";

    protected Product() {}

    public Product(UUID id, String sku, String name, long priceCents, String currency) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.priceCents = priceCents;
        this.currency = currency;
    }

    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public long getPriceCents() { return priceCents; }
    public String getCurrency() { return currency; }
}

