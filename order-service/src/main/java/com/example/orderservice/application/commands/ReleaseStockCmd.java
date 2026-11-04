package com.example.orderservice.application.commands;

import java.util.UUID;

/**
 * Command to release previously reserved stock (used on cancel, failure or timeout).
 */
public class ReleaseStockCmd implements OrderCommand {
    private final UUID orderId;
    private final String reason; // e.g., "PAYMENT_FAILED", "TIMEOUT", "CUSTOMER_CANCEL"

    public ReleaseStockCmd(UUID orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    @Override
    public UUID getOrderId() { return orderId; }

    @Override
    public String getTopic() { return com.example.common.kafka.Topics.INVENTORY_COMMANDS; }

    public String getReason() { return reason; }
}

