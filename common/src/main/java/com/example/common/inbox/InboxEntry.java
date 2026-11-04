package com.example.common.inbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbox table for idempotent consumer handling.
 */
@Entity
@Table(name = "processed_messages",
       uniqueConstraints = {@UniqueConstraint(columnNames = {"message_id", "consumer"})})
public class InboxEntry {
    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "consumer", nullable = false)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    protected InboxEntry() {}

    public InboxEntry(UUID messageId, String consumer) {
        this.messageId = messageId;
        this.consumer = consumer;
    }

    public UUID getMessageId() { return messageId; }
    public String getConsumer() { return consumer; }
    public Instant getProcessedAt() { return processedAt; }
}

