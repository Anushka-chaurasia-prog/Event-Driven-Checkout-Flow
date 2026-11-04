package com.example.paymentservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.example.common.inbox.InboxRepository;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.example.paymentservice.application.commands.RefundPaymentCmd;
import com.example.paymentservice.application.events.PaymentRefundedEvent;
import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.infrastructure.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefundPaymentHandler {

    private final PaymentRepository paymentRepo;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RefundPaymentHandler(PaymentRepository paymentRepo,
                                OutboxRepository outboxRepo,
                                InboxRepository inboxRepo) {
        this.paymentRepo = paymentRepo;
        this.outboxRepo = outboxRepo;
        this.inboxRepo = inboxRepo;
    }

    @Transactional
    public void handle(RefundPaymentCmd cmd, Envelope env) {
        if (!inboxRepo.tryInsert(env.eventId(), Groups.PAYMENT)) {
            return;
        }

        paymentRepo.findByOrderId(cmd.getOrderId()).ifPresent(p -> {
            p.setStatus(Payment.Status.REFUNDED);
            paymentRepo.save(p);
        });

        PaymentRefundedEvent event = new PaymentRefundedEvent(cmd.getOrderId(), cmd.getPaymentId());
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "PaymentRefunded",
                cmd.getOrderId(),
                env.correlationId() != null ? env.correlationId() : CorrelationIdGenerator.generate(),
                env.eventId().toString(),
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

        outboxRepo.save(new OutboxEvent(
                outEnv.eventId(),
                Topics.PAYMENT_EVENTS,
                cmd.getOrderId().toString(),
                outEnv.type(),
                payloadJson
        ));
    }
}

