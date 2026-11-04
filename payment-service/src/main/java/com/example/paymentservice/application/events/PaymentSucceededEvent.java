package com.example.paymentservice.application.events;

import java.util.UUID;

public class PaymentSucceededEvent {
    private UUID orderId;
    private UUID paymentId;

    public PaymentSucceededEvent() {}

    public PaymentSucceededEvent(UUID orderId, UUID paymentId) {
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
}

