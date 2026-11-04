package com.example.orderservice.application.commands;

import java.util.UUID;

/**
 * Command to request a refund for a previously successful payment.
 */
public class RefundPaymentCmd implements OrderCommand {
    private final UUID orderId;
    private final UUID paymentId;

    public RefundPaymentCmd(UUID orderId, UUID paymentId) {
        this.orderId = orderId;
        this.paymentId = paymentId;
    }

    @Override
    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public String getTopic() {
        return com.example.common.kafka.Topics.PAYMENT_COMMANDS;
    }

    public UUID getPaymentId() {
        return paymentId;
    }
}

