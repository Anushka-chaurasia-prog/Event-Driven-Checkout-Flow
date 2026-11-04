package com.example.orderservice.domain;

public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    CONFIRMED,
    REJECTED,
    CANCELLING,
    CANCELLED;

    public boolean isTerminal() {
        return this == CONFIRMED || this == REJECTED || this == CANCELLED;
    }
}

