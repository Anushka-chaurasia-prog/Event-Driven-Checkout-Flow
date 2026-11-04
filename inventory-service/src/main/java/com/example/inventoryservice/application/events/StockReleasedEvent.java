package com.example.inventoryservice.application.events;

import java.util.UUID;

/**
 * Event emitted when a reservation is released (either due to cancellation, timeout, or expiry).
 */
public class StockReleasedEvent {
    private final UUID orderId;
    private final String reason; // e.g., "TIMEOUT", "CUSTOMER_CANCEL", "EXPIRED", "PAYMENT_FAILED"

    public StockReleasedEvent(UUID orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public String getReason() { return reason; }
}

