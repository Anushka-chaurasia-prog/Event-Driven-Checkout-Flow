package com.example.inventoryservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Topics;
import com.example.inventoryservice.domain.Reservation;
import com.example.inventoryservice.domain.ReservationStatus;
import com.example.inventoryservice.domain.ReservationLine;
import com.example.inventoryservice.infrastructure.repository.ReservationRepository;
import com.example.inventoryservice.infrastructure.repository.StockRepository;
import com.example.inventoryservice.application.commands.ReleaseStockCmd;
import com.example.inventoryservice.application.events.StockReleasedEvent;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.inbox.InboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class ReleaseStockHandler {
    private final ReservationRepository reservationRepo;
    private final StockRepository stockRepo;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public ReleaseStockHandler(ReservationRepository reservationRepo,
                               StockRepository stockRepo,
                               OutboxRepository outboxRepo,
                               InboxRepository inboxRepo) {
        this.reservationRepo = reservationRepo;
        this.stockRepo = stockRepo;
        this.outboxRepo = outboxRepo;
        this.inboxRepo = inboxRepo;
    }

    @Transactional
    public void handle(ReleaseStockCmd cmd, Envelope env) {
        // deduplication guard
        if (!inboxRepo.tryInsert(env.eventId(), com.example.common.kafka.Groups.INVENTORY)) {
            return;
        }
        UUID orderId = cmd.getOrderId();
        Reservation reservation = reservationRepo.findByOrderId(orderId).orElse(null);
        if (reservation != null && reservation.getStatus() == ReservationStatus.RESERVED) {
            // reverse each line
            for (ReservationLine line : reservation.getLines()) {
                stockRepo.incrementAvailableAndDecrementReserved(line.getProductId(), line.getQuantity());
            }
            reservation.setStatus(ReservationStatus.RELEASED);
            reservationRepo.save(reservation);
            outboxRepo.save(toStockReleasedEvent(orderId, cmd.getReason(), env.correlationId(), env.eventId()));
        } else {
            // No reservation (or already released) – still publish a StockReleased event for idempotency
            outboxRepo.save(toStockReleasedEvent(orderId, cmd.getReason(), env.correlationId(), env.eventId()));
        }
    }

    private OutboxEvent toStockReleasedEvent(UUID orderId, String reason, String correlationId, UUID causationId) {
        StockReleasedEvent event = new StockReleasedEvent(orderId, reason);
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "StockReleased",
                orderId,
                correlationId != null ? correlationId : CorrelationIdGenerator.generate(),
                causationId.toString(),
                Instant.now(),
                1,
                mapper.valueToTree(event)
        );
        String payloadJson;
        try {
            payloadJson = mapper.writeValueAsString(outEnv);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize envelope", e);
        }
        return new OutboxEvent(outEnv.eventId(), Topics.INVENTORY_EVENTS, orderId.toString(), outEnv.type(), payloadJson);
    }
}

