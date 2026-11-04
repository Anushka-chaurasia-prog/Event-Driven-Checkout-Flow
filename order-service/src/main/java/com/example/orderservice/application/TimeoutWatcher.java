package com.example.orderservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.example.orderservice.application.commands.OrderCommand;
import com.example.orderservice.domain.Order;
import com.example.orderservice.infrastructure.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TimeoutWatcher {

    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final long timeoutSeconds;

    public TimeoutWatcher(OrderRepository orderRepo,
                          OutboxRepository outboxRepo,
                          @Value("${order.timeout.seconds:120}") long timeoutSeconds) {
        this.orderRepo = orderRepo;
        this.outboxRepo = outboxRepo;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${order.timeout.scan-interval-ms:15000}")
    @Transactional
    public void scan() {
        Instant deadline = Instant.now().minus(Duration.ofSeconds(timeoutSeconds));
        List<Order> staleOrders = orderRepo.findStaleOrders(deadline);
        for (Order order : staleOrders) {
            Optional<OrderCommand> cmdOpt = order.onTimeout();
            orderRepo.save(order);
            cmdOpt.ifPresent(cmd -> outboxRepo.save(toOutboxEvent(cmd)));
        }
    }

    private OutboxEvent toOutboxEvent(OrderCommand cmd) {
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                cmd.getClass().getSimpleName(),
                cmd.getOrderId(),
                CorrelationIdGenerator.generate(),
                UUID.randomUUID().toString(),
                Instant.now(),
                1,
                mapper.valueToTree(cmd)
        );
        String payloadJson;
        try {
            payloadJson = mapper.writeValueAsString(outEnv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize envelope", e);
        }
        return new OutboxEvent(
                outEnv.eventId(),
                cmd.getTopic(),
                cmd.getOrderId().toString(),
                outEnv.type(),
                payloadJson
        );
    }
}

