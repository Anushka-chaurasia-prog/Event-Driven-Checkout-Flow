package com.example.inventoryservice.application.commands;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.UUID;

/**
 * Command to reserve stock for an order – inventory service specific copy.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReserveStockCmd {
    private UUID orderId;
    private List<Item> items;
    private int reservationTtlSeconds;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
        @JsonProperty("productId") UUID productId,
        @JsonProperty("quantity") int quantity) {}

    public ReserveStockCmd() {}

    @JsonCreator
    public ReserveStockCmd(
            @JsonProperty("orderId") UUID orderId,
            @JsonProperty("items") List<Item> items,
            @JsonProperty("reservationTtlSeconds") int reservationTtlSeconds) {
        this.orderId = orderId;
        this.items = items;
        this.reservationTtlSeconds = reservationTtlSeconds;
    }

    public UUID getOrderId() { return orderId; }
    public List<Item> getItems() { return items; }
    public int getReservationTtlSeconds() { return reservationTtlSeconds; }
}
