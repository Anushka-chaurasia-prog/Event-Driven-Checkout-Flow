package com.example.inventoryservice.application.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ReleaseStockCmd {
    private UUID orderId;
    private String reason;

    public ReleaseStockCmd() {}

    @JsonCreator
    public ReleaseStockCmd(
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("reason") String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public UUID getOrderId() { return orderId; }
    public String getReason() { return reason; }
}
