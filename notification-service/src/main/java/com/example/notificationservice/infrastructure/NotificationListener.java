package com.example.notificationservice.infrastructure;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = Groups.NOTIFICATION)
    public void onOrderEvent(String message) {
        try {
            Envelope env = mapper.readValue(message, Envelope.class);
            log.info("[notification-service] Received event: type={}, orderId={}, correlationId={}",
                    env.type(), env.orderId(), env.correlationId());
        } catch (Exception e) {
            log.info("[notification-service] Received raw message: {}", message);
        }
    }
}
