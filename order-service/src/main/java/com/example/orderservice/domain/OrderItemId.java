package com.example.orderservice.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class OrderItemId implements Serializable {
    private UUID orderId;
    private UUID productId;

    public OrderItemId() {}

    public OrderItemId(UUID orderId, UUID productId) {
        this.orderId = orderId;
        this.productId = productId;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItemId that)) return false;
        return Objects.equals(orderId, that.orderId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId, productId);
    }
}

