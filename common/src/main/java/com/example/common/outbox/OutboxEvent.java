package com.example.common.outbox;

import com.example.common.envelope.Envelope;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbox table used for the transactional outbox pattern.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    private UUID id; // same as envelope.eventId

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "msg_key", nullable = false)
    private String msgKey; // usually orderId as string

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload; // JSON string of the envelope

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {}

    public OutboxEvent(UUID id, String topic, String msgKey, String type, String payload) {
        this.id = id;
        this.topic = topic;
        this.msgKey = msgKey;
        this.type = type;
        this.payload = payload;
    }

    // getters / setters
    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getMsgKey() { return msgKey; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}

