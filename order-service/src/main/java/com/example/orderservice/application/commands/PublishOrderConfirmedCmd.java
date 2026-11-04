package com.example.orderservice.application.commands;

import java.util.UUID;

/**
 * Command that signals the order has been successfully confirmed.
 */
public class PublishOrderConfirmedCmd implements OrderCommand {
    private final UUID orderId;

    public PublishOrderConfirmedCmd(UUID orderId) {
        this.orderId = orderId;
    }

    @Override
    public UUID getOrderId() {
        return orderId;
    }

    @Override
    public String getTopic() {
        return com.example.common.kafka.Topics.ORDER_EVENTS;
    }
}

