package com.example.orderservice.domain.events;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StockReservedEvent {
    private UUID orderId;
    private List<Item> items;
    private long totalCents;
    private String currency;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private UUID productId;
        private int quantity;
        private long unitPriceCents;

        public Item() {}

        public Item(UUID productId, int quantity, long unitPriceCents) {
            this.productId = productId;
            this.quantity = quantity;
            this.unitPriceCents = unitPriceCents;
        }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public long getUnitPriceCents() { return unitPriceCents; }
        public void setUnitPriceCents(long unitPriceCents) { this.unitPriceCents = unitPriceCents; }
    }

    public StockReservedEvent() {}

    public StockReservedEvent(UUID orderId, List<Item> items, long totalCents, String currency) {
        this.orderId = orderId;
        this.items = items;
        this.totalCents = totalCents;
        this.currency = currency;
    }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public long getTotalCents() { return totalCents; }
    public void setTotalCents(long totalCents) { this.totalCents = totalCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
