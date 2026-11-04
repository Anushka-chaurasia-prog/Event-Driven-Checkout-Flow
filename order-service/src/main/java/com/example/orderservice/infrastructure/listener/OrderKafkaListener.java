package com.example.orderservice.infrastructure.listener;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.example.orderservice.application.SagaHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(OrderKafkaListener.class);
    private final SagaHandler sagaHandler;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OrderKafkaListener(SagaHandler sagaHandler) {
        this.sagaHandler = sagaHandler;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS}, groupId = Groups.ORDER)
    public void onMessage(String message) {
        try {
            Envelope env = objectMapper.readValue(message, Envelope.class);
            log.info("[order-service] Received event: type={}, orderId={}", env.type(), env.orderId());
            sagaHandler.handle(env);
        } catch (Exception e) {
            log.error("[order-service] Failed to process order saga event: " + e.getMessage(), e);
        }
    }
}
