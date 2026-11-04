package com.example.inventoryservice.application.events;

import java.util.UUID;

/**
 * Event emitted when the reservation cannot be made (insufficient stock or other error).
 */
public class StockRejectedEvent {
    private final UUID orderId;
    private final String reason;

    public StockRejectedEvent(UUID orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public String getReason() { return reason; }
}

