package com.example.orderservice.application.commands;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.UUID;

/**
 * Marker interface for commands that the saga can emit.
 * Each command knows which Kafka topic it should be sent to.
 */
public interface OrderCommand {
    UUID getOrderId();

    @JsonIgnore
    String getTopic();
}
