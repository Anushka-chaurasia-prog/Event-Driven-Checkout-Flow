package com.example.inventoryservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Topics;
import com.example.inventoryservice.domain.Reservation;
import com.example.inventoryservice.domain.ReservationStatus;
import com.example.inventoryservice.domain.ReservationLine;
import com.example.inventoryservice.domain.Stock;
import com.example.inventoryservice.domain.Product;
import com.example.inventoryservice.domain.ReservationLineId;
import com.example.inventoryservice.infrastructure.repository.ReservationRepository;
import com.example.inventoryservice.infrastructure.repository.StockRepository;
import com.example.inventoryservice.infrastructure.repository.ProductRepository;
import com.example.inventoryservice.application.commands.ReserveStockCmd;
import com.example.inventoryservice.application.commands.ReleaseStockCmd;
import com.example.inventoryservice.application.events.StockReservedEvent;
import com.example.inventoryservice.application.events.StockRejectedEvent;
import com.example.inventoryservice.application.events.StockReleasedEvent;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.inbox.InboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReserveStockHandler {
    private static final Logger log = LoggerFactory.getLogger(ReserveStockHandler.class);
    private final StockRepository stockRepo;
    private final ProductRepository productRepo;
    private final ReservationRepository reservationRepo;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ReserveStockHandler(StockRepository stockRepo,
                               ProductRepository productRepo,
                               ReservationRepository reservationRepo,
                               OutboxRepository outboxRepo,
                               InboxRepository inboxRepo) {
        this.stockRepo = stockRepo;
        this.productRepo = productRepo;
        this.reservationRepo = reservationRepo;
        this.outboxRepo = outboxRepo;
        this.inboxRepo = inboxRepo;
    }

    @Transactional
    public void handle(ReserveStockCmd cmd, Envelope env) {
        // deduplicate
        if (!inboxRepo.tryInsert(env.eventId(), com.example.common.kafka.Groups.INVENTORY)) {
            log.info("[inventory-service] Duplicate command ignored: {}", env.eventId());
            return;
        }
        UUID orderId = cmd.getOrderId();
        log.info("[inventory-service] Handling ReserveStockCmd for order {}", orderId);
        // idempotency – if reservation already exists for this order, re‑publish StockReserved
        if (reservationRepo.existsByOrderId(orderId)) {
            log.info("[inventory-service] Reservation already exists for order {}, re-publishing StockReserved", orderId);
            Reservation existing = reservationRepo.findByOrderId(orderId).orElseThrow();
            outboxRepo.save(toStockReservedEvent(existing, 0L, env.correlationId(), env.eventId()));
            return;
        }
        List<ReserveStockCmd.Item> items = cmd.getItems() != null ? cmd.getItems() : List.of();
        // Sort items by productId for deterministic lock ordering
        List<ReserveStockCmd.Item> sortedItems = items.stream()
                .sorted(Comparator.comparing(ReserveStockCmd.Item::productId))
                .collect(Collectors.toList());

        // Attempt atomic updates for each line
        for (ReserveStockCmd.Item item : sortedItems) {
            int updated = stockRepo.decrementAvailableAndIncrementReserved(item.productId(), item.quantity());
            if (updated == 0) {
                log.warn("[inventory-service] Insufficient stock for product {} (qty: {}), rejecting order {}",
                        item.productId(), item.quantity(), orderId);
                outboxRepo.save(toStockRejectedEvent(orderId, "INSUFFICIENT_STOCK", env.correlationId(), env.eventId()));
                return;
            }
        }
        // All stock updates succeeded – create reservation
        int ttl = cmd.getReservationTtlSeconds() > 0 ? cmd.getReservationTtlSeconds() : 120;
        Reservation reservation = new Reservation(UUID.randomUUID(), orderId, ReservationStatus.RESERVED,
                Instant.now().plusSeconds(ttl));
        long totalCents = 0L;
        for (ReserveStockCmd.Item item : sortedItems) {
            Product product = productRepo.findById(item.productId()).orElseThrow();
            ReservationLine line = new ReservationLine(reservation.getId(), item.productId(), item.quantity(), product.getPriceCents());
            reservation.addLine(line);
            totalCents += product.getPriceCents() * item.quantity();
        }
        reservationRepo.save(reservation);
        log.info("[inventory-service] Successfully reserved stock for order {}, totalCents={}", orderId, totalCents);
        // Emit StockReserved event
        outboxRepo.save(toStockReservedEvent(reservation, totalCents, env.correlationId(), env.eventId()));
    }

    private OutboxEvent toStockReservedEvent(Reservation reservation, long totalCents, String correlationId, UUID causationId) {
        StockReservedEvent event = new StockReservedEvent(
                reservation.getOrderId(),
                reservation.getLines().stream()
                        .map(l -> new StockReservedEvent.Item(l.getProductId(), l.getQuantity(), l.getUnitPriceCents()))
                        .collect(Collectors.toList()),
                totalCents,
                "INR");
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "StockReserved",
                reservation.getOrderId(),
                correlationId != null ? correlationId : CorrelationIdGenerator.generate(),
                causationId.toString(),
                Instant.now(),
                1,
                mapper.valueToTree(event));
        String payloadJson;
        try {
            payloadJson = mapper.writeValueAsString(outEnv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize envelope", e);
        }
        return new OutboxEvent(outEnv.eventId(), Topics.INVENTORY_EVENTS, reservation.getOrderId().toString(), outEnv.type(), payloadJson);
    }

    private OutboxEvent toStockReservedEvent(Reservation reservation, Envelope env) {
        return toStockReservedEvent(reservation, 0L, env.correlationId(), env.eventId());
    }

    private OutboxEvent toStockRejectedEvent(UUID orderId, String reason, String correlationId, UUID causationId) {
        StockRejectedEvent event = new StockRejectedEvent(orderId, reason);
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "StockRejected",
                orderId,
                correlationId != null ? correlationId : CorrelationIdGenerator.generate(),
                causationId.toString(),
                Instant.now(),
                1,
                mapper.valueToTree(event));
        String payloadJson;
        try {
            payloadJson = mapper.writeValueAsString(outEnv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize envelope", e);
        }
        return new OutboxEvent(outEnv.eventId(), Topics.INVENTORY_EVENTS, orderId.toString(), outEnv.type(), payloadJson);
    }
}
