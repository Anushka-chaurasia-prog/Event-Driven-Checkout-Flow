package com.example.orderservice.application.commands;

import java.util.UUID;

/**
 * Command to reserve stock for an order.
 */
public class ReserveStockCmd implements OrderCommand {
    private final UUID orderId;
    private final java.util.List<Item> items;
    private final int reservationTtlSeconds;

    public record Item(UUID productId, int quantity) {}

    public ReserveStockCmd(UUID orderId, java.util.List<Item> items, int reservationTtlSeconds) {
        this.orderId = orderId;
        this.items = items;
        this.reservationTtlSeconds = reservationTtlSeconds;
    }

    @Override
    public UUID getOrderId() { return orderId; }

    @Override
    public String getTopic() { return com.example.common.kafka.Topics.INVENTORY_COMMANDS; }

    public java.util.List<Item> getItems() { return items; }
    public int getReservationTtlSeconds() { return reservationTtlSeconds; }
}

