package com.example.paymentservice.application.commands;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Command to charge a payment for an order.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChargePaymentCmd {
    private UUID orderId;
    private UUID userId;
    private long amountCents;
    private String currency;

    public ChargePaymentCmd() {}

    public ChargePaymentCmd(UUID orderId, UUID userId, long amountCents, String currency) {
        this.orderId = orderId;
        this.userId = userId;
        this.amountCents = amountCents;
        this.currency = currency;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long amountCents) { this.amountCents = amountCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
