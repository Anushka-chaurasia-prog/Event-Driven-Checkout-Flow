package com.example.paymentservice.infrastructure.listener;

import com.example.common.envelope.Envelope;
import com.example.common.kafka.Groups;
import com.example.common.kafka.Topics;
import com.example.paymentservice.application.ChargePaymentHandler;
import com.example.paymentservice.application.RefundPaymentHandler;
import com.example.paymentservice.application.commands.ChargePaymentCmd;
import com.example.paymentservice.application.commands.RefundPaymentCmd;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentKafkaListener.class);
    private final ChargePaymentHandler chargeHandler;
    private final RefundPaymentHandler refundHandler;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public PaymentKafkaListener(ChargePaymentHandler chargeHandler, RefundPaymentHandler refundHandler) {
        this.chargeHandler = chargeHandler;
        this.refundHandler = refundHandler;
    }

    @KafkaListener(topics = Topics.PAYMENT_COMMANDS, groupId = Groups.PAYMENT)
    public void onCommand(String message) {
        try {
            Envelope env = mapper.readValue(message, Envelope.class);
            log.info("[payment-service] Received command: type={}, orderId={}", env.type(), env.orderId());
            switch (env.type()) {
                case "ChargePaymentCmd" -> {
                    ChargePaymentCmd cmd = mapper.convertValue(env.payload(), ChargePaymentCmd.class);
                    chargeHandler.handle(cmd, env);
                }
                case "RefundPaymentCmd" -> {
                    RefundPaymentCmd cmd = mapper.convertValue(env.payload(), RefundPaymentCmd.class);
                    refundHandler.handle(cmd, env);
                }
                default -> log.warn("[payment-service] Unknown command type: {}", env.type());
            }
        } catch (Exception e) {
            log.error("[payment-service] Failed to process payment command: " + e.getMessage(), e);
        }
    }
}
