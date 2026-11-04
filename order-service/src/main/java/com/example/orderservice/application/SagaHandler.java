package com.example.orderservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.example.common.inbox.InboxRepository;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.orderservice.application.commands.OrderCommand;
import com.example.orderservice.application.commands.ReserveStockCmd;
import com.example.orderservice.application.commands.ChargePaymentCmd;
import com.example.orderservice.application.commands.ReleaseStockCmd;
import com.example.orderservice.application.commands.PublishOrderConfirmedCmd;
import com.example.orderservice.application.commands.RefundPaymentCmd;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.events.PaymentFailedEvent;
import com.example.orderservice.domain.events.PaymentSucceededEvent;
import com.example.orderservice.domain.events.StockRejectedEvent;
import com.example.orderservice.domain.events.StockReleasedEvent;
import com.example.orderservice.domain.events.StockReservedEvent;
import com.example.orderservice.infrastructure.repository.OrderRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class SagaHandler {
    private static final Logger log = LoggerFactory.getLogger(SagaHandler.class);
    private final InboxRepository inboxRepo;
    private final OrderRepository orderRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SagaHandler(InboxRepository inboxRepo, OrderRepository orderRepo, OutboxRepository outboxRepo) {
        this.inboxRepo = inboxRepo;
        this.orderRepo = orderRepo;
        this.outboxRepo = outboxRepo;
    }

    @Transactional
    public void handle(Envelope env) {
        // Idempotent consumer guard – tryInsert returns true if this is the first processing
        if (!inboxRepo.tryInsert(env.eventId(), Groups.ORDER)) {
            log.info("[order-service] Duplicate event ignored: {}", env.eventId());
            return; // duplicate – ignore safely
        }
        Order order = orderRepo.findById(env.orderId())
                .orElseThrow(() -> new IllegalStateException("Order not found: " + env.orderId()));
        log.info("[order-service] Handling event {} for order {}, current status={}", env.type(), order.getId(), order.getStatus());
        Optional<OrderCommand> next = dispatch(order, env);
        orderRepo.save(order);
        log.info("[order-service] Order {} updated to status={}", order.getId(), order.getStatus());
        next.ifPresent(cmd -> {
            log.info("[order-service] Next command to emit: {}", cmd.getClass().getSimpleName());
            outboxRepo.save(toOutboxEvent(cmd, env.correlationId(), env.eventId()));
        });
    }

    private Optional<OrderCommand> dispatch(Order order, Envelope env) {
        try {
            return switch (env.type()) {
                case "StockReserved" -> order.onStockReserved(deserialize(env.payload(), StockReservedEvent.class));
                case "StockRejected" -> order.onStockRejected(deserialize(env.payload(), StockRejectedEvent.class));
                case "PaymentSucceeded" -> order.onPaymentSucceeded(deserialize(env.payload(), PaymentSucceededEvent.class));
                case "PaymentFailed" -> order.onPaymentFailed(deserialize(env.payload(), PaymentFailedEvent.class));
                case "StockReleased" -> order.onStockReleased(deserialize(env.payload(), StockReleasedEvent.class));
                default -> {
                    log.warn("[order-service] Unknown event type in saga: {}", env.type());
                    yield Optional.empty();
                }
            };
        } catch (Exception e) {
            log.error("[order-service] Failed to dispatch event " + env.type() + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    private <T> T deserialize(com.fasterxml.jackson.databind.JsonNode node, Class<T> clazz) {
        return mapper.convertValue(node, clazz);
    }

    private OutboxEvent toOutboxEvent(OrderCommand cmd, String correlationId, UUID causationId) {
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                cmd.getClass().getSimpleName(),
                cmd.getOrderId(),
                correlationId != null ? correlationId : CorrelationIdGenerator.generate(),
                causationId.toString(),
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
