package com.example.inventoryservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Topics;
import com.example.inventoryservice.domain.Reservation;
import com.example.inventoryservice.domain.ReservationStatus;
import com.example.inventoryservice.application.events.StockReleasedEvent;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.inbox.InboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Periodic job that expires reservations whose TTL has passed.
 * Runs every 30 seconds, finds RESERVED rows with expiresAt < now,
 * releases the stock and publishes a StockReleased event with reason "EXPIRED".
 */
@Component
public class ExpiryJob {
    private final com.example.inventoryservice.infrastructure.repository.ReservationRepository reservationRepo;
    private final com.example.inventoryservice.infrastructure.repository.StockRepository stockRepo;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public ExpiryJob(com.example.inventoryservice.infrastructure.repository.ReservationRepository reservationRepo,
                     com.example.inventoryservice.infrastructure.repository.StockRepository stockRepo,
                     OutboxRepository outboxRepo,
                     InboxRepository inboxRepo) {
        this.reservationRepo = reservationRepo;
        this.stockRepo = stockRepo;
        this.outboxRepo = outboxRepo;
        this.inboxRepo = inboxRepo;
    }

    @Scheduled(fixedDelay = 30000) // every 30 s
    @Transactional
    public void expireReservations() {
        List<Reservation> toExpire = reservationRepo.findByStatusAndExpiresAtBefore(ReservationStatus.RESERVED, Instant.now());
        for (Reservation res : toExpire) {
            // reverse stock for each line
            for (var line : res.getLines()) {
                stockRepo.incrementAvailableAndDecrementReserved(line.getProductId(), line.getQuantity());
            }
            res.setStatus(ReservationStatus.EXPIRED);
            reservationRepo.save(res);
            // publish StockReleased event (reason EXPIRED)
            outboxRepo.save(toStockReleasedEvent(res.getOrderId(), "EXPIRED", null, null));
        }
    }

    private OutboxEvent toStockReleasedEvent(UUID orderId, String reason, String correlationId, UUID causationId) {
        StockReleasedEvent event = new StockReleasedEvent(orderId, reason);
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "StockReleased",
                orderId,
                correlationId != null ? correlationId : CorrelationIdGenerator.generate(),
                causationId != null ? causationId.toString() : "",
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

