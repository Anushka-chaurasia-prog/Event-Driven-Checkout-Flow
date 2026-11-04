package com.example.common.envelope;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/**
 * Standard message envelope used for all Kafka events and commands.
 */
public record Envelope(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("type") String type,
        @JsonProperty("orderId") UUID orderId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("causationId") String causationId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("schemaVersion") int schemaVersion,
        @JsonProperty("payload") JsonNode payload) {
}

