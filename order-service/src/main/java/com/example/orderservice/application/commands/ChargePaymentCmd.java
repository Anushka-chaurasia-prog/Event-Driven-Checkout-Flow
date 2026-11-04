package com.example.orderservice.application.commands;

import java.util.UUID;

/**
 * Command to charge the payment for an order.
 */
public class ChargePaymentCmd implements OrderCommand {
    private final UUID orderId;
    private final UUID userId;
    private final long amountCents;
    private final String currency;

    public ChargePaymentCmd(UUID orderId, UUID userId, long amountCents, String currency) {
        this.orderId = orderId;
        this.userId = userId;
        this.amountCents = amountCents;
        this.currency = currency;
    }

    @Override
    public UUID getOrderId() { return orderId; }

    @Override
    public String getTopic() { return com.example.common.kafka.Topics.PAYMENT_COMMANDS; }

    public UUID getUserId() { return userId; }
    public long getAmountCents() { return amountCents; }
    public String getCurrency() { return currency; }
}

