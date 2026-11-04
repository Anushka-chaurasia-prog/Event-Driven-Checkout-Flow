package com.example.inventoryservice.application.events;

import java.util.List;
import java.util.UUID;

/**
 * Event emitted when stock has been successfully reserved for an order.
 */
public class StockReservedEvent {
    private final UUID orderId;
    private final List<Item> items; // each line includes unit price
    private final long totalCents;
    private final String currency;

    public record Item(UUID productId, int quantity, long unitPriceCents) {}

    public StockReservedEvent(UUID orderId, List<Item> items, long totalCents, String currency) {
        this.orderId = orderId;
        this.items = items;
        this.totalCents = totalCents;
        this.currency = currency;
    }

    public UUID getOrderId() { return orderId; }
    public List<Item> getItems() { return items; }
    public long getTotalCents() { return totalCents; }
    public String getCurrency() { return currency; }
}

