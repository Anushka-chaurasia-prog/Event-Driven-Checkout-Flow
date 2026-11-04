package com.example.paymentservice.application;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.example.common.inbox.InboxRepository;
import com.example.common.outbox.OutboxEvent;
import com.example.common.outbox.OutboxRepository;
import com.example.common.util.CorrelationIdGenerator;
import com.example.paymentservice.application.commands.ChargePaymentCmd;
import com.example.paymentservice.application.events.PaymentSucceededEvent;
import com.example.paymentservice.domain.Payment;
import com.example.paymentservice.infrastructure.repository.PaymentRepository;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ChargePaymentHandler {

    private static final Logger log = LoggerFactory.getLogger(ChargePaymentHandler.class);
    private final PaymentRepository paymentRepo;
    private final OutboxRepository outboxRepo;
    private final InboxRepository inboxRepo;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public ChargePaymentHandler(PaymentRepository paymentRepo,
                                OutboxRepository outboxRepo,
                                InboxRepository inboxRepo) {
        this.paymentRepo = paymentRepo;
        this.outboxRepo = outboxRepo;
        this.inboxRepo = inboxRepo;
    }

    @Transactional
    public void handle(ChargePaymentCmd cmd, Envelope env) {
        if (!inboxRepo.tryInsert(env.eventId(), Groups.PAYMENT)) {
            log.info("[payment-service] Duplicate command ignored: {}", env.eventId());
            return;
        }

        UUID paymentId = UUID.randomUUID();
        Payment payment = paymentRepo.findByOrderId(cmd.getOrderId())
                .orElseGet(() -> new Payment(
                        paymentId,
                        cmd.getOrderId(),
                        BigDecimal.valueOf(cmd.getAmountCents()),
                        Payment.Status.SUCCESS,
                        Instant.now()
                ));

        payment.setStatus(Payment.Status.SUCCESS);
        paymentRepo.save(payment);
        log.info("[payment-service] Successfully processed payment {} for order {}", payment.getId(), cmd.getOrderId());

        PaymentSucceededEvent event = new PaymentSucceededEvent(cmd.getOrderId(), payment.getId());
        Envelope outEnv = new Envelope(
                UUID.randomUUID(),
                "PaymentSucceeded",
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
        log.info("[payment-service] Saved PaymentSucceeded outbox event for order {}", cmd.getOrderId());
    }
}
