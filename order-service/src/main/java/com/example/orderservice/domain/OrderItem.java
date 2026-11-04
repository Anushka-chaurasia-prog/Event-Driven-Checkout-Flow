package com.example.orderservice.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@IdClass(OrderItemId.class)
public class OrderItem {
    @Id
    @Column(name = "order_id")
    private UUID orderId; // part of PK together with productId

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents")
    private Long unitPriceCents;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private Order order;

    protected OrderItem() {}

    public OrderItem(UUID orderId, UUID productId, int quantity) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Long getUnitPriceCents() { return unitPriceCents; }
    public void setUnitPriceCents(Long unitPriceCents) { this.unitPriceCents = unitPriceCents; }
    public void setOrder(Order order) { this.order = order; }
}

