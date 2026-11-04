package com.example.inventoryservice.infrastructure.listener;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.example.inventoryservice.application.ReserveStockHandler;
import com.example.inventoryservice.application.ReleaseStockHandler;
import com.example.inventoryservice.application.commands.ReleaseStockCmd;
import com.example.inventoryservice.application.commands.ReserveStockCmd;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens for inventory command messages and forwards them to the appropriate handler.
 */
@Component
public class InventoryKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryKafkaListener.class);
    private final ReserveStockHandler reserveHandler;
    private final ReleaseStockHandler releaseHandler;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public InventoryKafkaListener(ReserveStockHandler reserveHandler, ReleaseStockHandler releaseHandler) {
        this.reserveHandler = reserveHandler;
        this.releaseHandler = releaseHandler;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = Groups.INVENTORY)
    public void onCommand(String message) {
        try {
            Envelope env = objectMapper.readValue(message, Envelope.class);
            log.info("[inventory-service] Received command: type={}, orderId={}", env.type(), env.orderId());
            switch (env.type()) {
                case "ReserveStockCmd" -> {
                    ReserveStockCmd cmd = objectMapper.convertValue(env.payload(), ReserveStockCmd.class);
                    reserveHandler.handle(cmd, env);
                }
                case "ReleaseStockCmd" -> {
                    ReleaseStockCmd cmd = objectMapper.convertValue(env.payload(), ReleaseStockCmd.class);
                    releaseHandler.handle(cmd, env);
                }
                default -> log.warn("[inventory-service] Unknown command type: {}", env.type());
            }
        } catch (Exception e) {
            log.error("[inventory-service] Failed to process inventory command: " + e.getMessage(), e);
        }
    }
}
