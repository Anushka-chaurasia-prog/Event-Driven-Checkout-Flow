package com.example.paymentservice.application.events;

import java.util.UUID;

public class PaymentRefundedEvent {
    private UUID orderId;
    private UUID paymentId;

    public PaymentRefundedEvent() {}

    public PaymentRefundedEvent(UUID orderId, UUID paymentId) {
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
}

