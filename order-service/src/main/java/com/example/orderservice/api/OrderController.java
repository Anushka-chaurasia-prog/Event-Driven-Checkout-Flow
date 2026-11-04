package com.example.orderservice.api;

import com.example.common.envelope.Envelope;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.example.orderservice.api.dto.CreateOrderRequest;
import com.example.orderservice.api.dto.OrderResponse;
import com.example.orderservice.application.commands.ReserveStockCmd;
import com.example.orderservice.domain.Order;
import com.example.orderservice.domain.OrderItem;
import com.example.orderservice.infrastructure.repository.OrderRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OrderController(OrderRepository orderRepository, OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestBody CreateOrderRequest request) {

        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : UUID.randomUUID().toString();

        Optional<Order> existing = orderRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Order o = existing.get();
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(o.getId(), o.getStatus()));
        }

        UUID userId;
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                userId = UUID.fromString(userIdHeader.trim());
            } catch (Exception e) {
                userId = UUID.randomUUID();
            }
        } else if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().getPrincipal() instanceof UUID u) {
            userId = u;
        } else {
            userId = UUID.randomUUID();
        }

        UUID orderId = UUID.randomUUID();
        Order order = new Order(orderId, userId, key);
        if (request.getItems() != null) {
            for (CreateOrderRequest.ItemRequest ir : request.getItems()) {
                OrderItem item = new OrderItem(orderId, ir.getProductId(), ir.getQuantity());
                order.addItem(item);
            }
        }
        orderRepository.save(order);

        List<ReserveStockCmd.Item> cmdItems = request.getItems() == null ? List.of() :
                request.getItems().stream()
                        .map(i -> new ReserveStockCmd.Item(i.getProductId(), i.getQuantity()))
                        .toList();

        ReserveStockCmd cmd = new ReserveStockCmd(orderId, cmdItems, 120);
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "ReserveStockCmd",
                orderId,
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

        outboxRepository.save(new OutboxEvent(
                outEnv.eventId(),
                cmd.getTopic(),
                orderId.toString(),
                outEnv.type(),
                payloadJson
        ));

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new OrderResponse(order.getId(), order.getStatus()));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable("orderId") UUID orderId) {
        return orderRepository.findById(orderId)
                .map(o -> ResponseEntity.ok(new OrderResponse(o.getId(), o.getStatus())))
                .orElse(ResponseEntity.notFound().build());
    }
}

