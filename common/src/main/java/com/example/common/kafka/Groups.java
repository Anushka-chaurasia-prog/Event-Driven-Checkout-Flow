package com.example.common.kafka;

/**
 * Consumer group names shared across services.
 */
public final class Groups {
    public static final String ORDER = "order-service";
    public static final String INVENTORY = "inventory-service";
    public static final String PAYMENT = "payment-service";
    public static final String NOTIFICATION = "notification-service";
    private Groups() {}
}

