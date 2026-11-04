package com.example.orderservice.infrastructure.outbox;

import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OrderOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OrderOutboxRelay.class);
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OrderOutboxRelay(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    public void relay() {
        List<OutboxEvent> pending;
        try {
            pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByCreatedAt();
        } catch (Exception e) {
            log.warn("Failed to fetch outbox events: {}", e.getMessage());
            return;
        }
        if (pending.isEmpty()) {
            return;
        }
        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getMsgKey(), event.getPayload()).get(5, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId(), Instant.now());
                log.info("Published outbox event {} ({}) to topic {}", event.getId(), event.getType(), event.getTopic());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {}: {}", event.getId(), e.getMessage());
                break;
            }
        }
    }
}
