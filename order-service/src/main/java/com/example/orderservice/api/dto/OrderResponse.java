package com.example.orderservice.api.dto;

import com.example.orderservice.domain.OrderStatus;
import java.util.UUID;

public class OrderResponse {
    private UUID orderId;
    private OrderStatus status;

    public OrderResponse() {}

    public OrderResponse(UUID orderId, OrderStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
}

