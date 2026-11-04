package com.example.orderservice.api.dto;

import java.util.List;
import java.util.UUID;

public class CreateOrderRequest {
    private List<ItemRequest> items;

    public static class ItemRequest {
        private UUID productId;
        private int quantity;

        public ItemRequest() {}
        public ItemRequest(UUID productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
    }

    public CreateOrderRequest() {}
    public CreateOrderRequest(List<ItemRequest> items) { this.items = items; }

    public List<ItemRequest> getItems() { return items; }
    public void setItems(List<ItemRequest> items) { this.items = items; }
}

