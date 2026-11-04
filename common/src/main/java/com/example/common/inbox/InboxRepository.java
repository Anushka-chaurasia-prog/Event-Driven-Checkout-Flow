package com.example.common.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/**
 * Repository for the inbox (processed_messages) table.
 * Provides an atomic INSERT‑ON‑CONFLICT‑DO‑NOTHING to detect duplicates.
 */
public interface InboxRepository extends JpaRepository<InboxEntry, UUID> {
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO processed_messages (message_id, consumer, processed_at) VALUES (:msgId, :consumer, now()) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("msgId") UUID msgId, @Param("consumer") String consumer);

    /**
     * Attempts to insert a record for the given message/consumer. Returns true if the insert succeeded (i.e. first processing).
     */
    default boolean tryInsert(UUID msgId, String consumer) {
        return insertIfAbsent(msgId, consumer) > 0;
    }
}
