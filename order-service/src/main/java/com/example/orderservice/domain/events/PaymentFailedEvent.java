package com.example.orderservice.domain.events;

import java.util.UUID;

public class PaymentFailedEvent {
    private UUID orderId;
    private String reason;

    public PaymentFailedEvent() {}

    public PaymentFailedEvent(UUID orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

